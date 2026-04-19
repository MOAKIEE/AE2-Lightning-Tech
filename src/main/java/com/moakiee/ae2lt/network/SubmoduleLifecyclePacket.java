package com.moakiee.ae2lt.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.OverloadArmorItem;
import com.moakiee.ae2lt.overload.armor.OverloadArmorCarrierLocator;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorFeatureCatalog;

/**
 * Server-authoritative submodule lifecycle broadcast. The server detects the edge (a submodule
 * becomes active/inactive) and tells the owning client to fire the mirroring lifecycle hook on
 * its side. This avoids the client having to independently detect equipment changes, which is
 * unreliable for Curios slots (no onUnequip fires client-side after the stack leaves the slot)
 * and lagged for vanilla armor slots (tick ordering vs. LivingEquipmentChangeEvent).
 */
public record SubmoduleLifecyclePacket(UUID armorId, String submoduleId, byte action)
        implements CustomPacketPayload {
    public static final byte ACTION_ACTIVATE = 1;
    public static final byte ACTION_DEACTIVATE = 0;

    public static final Type<SubmoduleLifecyclePacket> TYPE =
            new Type<>(NetworkInit.id("submodule_lifecycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmoduleLifecyclePacket> STREAM_CODEC =
            StreamCodec.ofMember(SubmoduleLifecyclePacket::write, SubmoduleLifecyclePacket::decode);

    @Override
    public Type<SubmoduleLifecyclePacket> type() {
        return TYPE;
    }

    public static SubmoduleLifecyclePacket decode(RegistryFriendlyByteBuf buf) {
        UUID armorId = buf.readUUID();
        String submoduleId = buf.readUtf();
        byte action = buf.readByte();
        return new SubmoduleLifecyclePacket(armorId, submoduleId, action);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeUtf(submoduleId);
        buf.writeByte(action);
    }

    public static void broadcast(ServerPlayer player, UUID armorId, String submoduleId, boolean active) {
        if (armorId == null || submoduleId == null || submoduleId.isBlank()) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new SubmoduleLifecyclePacket(armorId, submoduleId, active ? ACTION_ACTIVATE : ACTION_DEACTIVATE));
    }

    public static void handle(SubmoduleLifecyclePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || !player.level().isClientSide()) {
                return;
            }
            payload.applyOnClient(player);
        });
    }

    private void applyOnClient(Player player) {
        var submodule = OverloadArmorFeatureCatalog.findSubmoduleById(submoduleId);
        if (submodule == null) {
            return;
        }

        ItemStack stack = resolveArmor(player);
        if (stack == null) {
            return;
        }

        boolean active = action == ACTION_ACTIVATE;
        OverloadArmorState.markClientActive(armorId, submoduleId, active);
        if (active) {
            submodule.onActivated(player, Dist.CLIENT, stack);
        } else {
            submodule.onDeactivated(player, Dist.CLIENT, stack);
        }
    }

    private ItemStack resolveArmor(Player player) {
        // 1) Check equipped slots (chest + curios). Covers the steady-state case.
        var carrier = OverloadArmorCarrierLocator.findEquipped(player, armorId);
        if (carrier != null) {
            return carrier.armorStack();
        }

        // 2) ACTIVATE may arrive in the same tick as the slot-sync packet but be processed
        //    slightly earlier; scan the player's inventory to catch the armor in transit.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof OverloadArmorItem
                    && armorId.equals(OverloadArmorState.getArmorId(stack))) {
                return stack;
            }
        }

        // 3) DEACTIVATE on fast drop: the stack has already left every slot we can see. Fall
        //    back to the last stack the client ticked for this armorId so submodules can still
        //    receive a meaningful onDeactivated call.
        return OverloadArmorItem.consumeLastKnownClientStack(armorId);
    }
}
