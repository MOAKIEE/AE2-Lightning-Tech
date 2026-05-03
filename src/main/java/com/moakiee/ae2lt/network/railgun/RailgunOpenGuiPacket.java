package com.moakiee.ae2lt.network.railgun;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.menu.railgun.RailgunMenu;
import com.moakiee.ae2lt.network.NetworkInit;

/** Client to server: G key opens the railgun module GUI. Skipped if charging. */
public record RailgunOpenGuiPacket(InteractionHand hand) implements CustomPacketPayload {

    public static final Type<RailgunOpenGuiPacket> TYPE =
            new Type<>(NetworkInit.id("railgun_open_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunOpenGuiPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], InteractionHand::ordinal),
                    RailgunOpenGuiPacket::hand,
                    RailgunOpenGuiPacket::new);

    @Override
    public Type<RailgunOpenGuiPacket> type() {
        return TYPE;
    }

    public static void handle(RailgunOpenGuiPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            var stack = p.getItemInHand(pkt.hand());
            if (!(stack.getItem() instanceof ElectromagneticRailgunItem)) return;
            if (p.isUsingItem() && p.getUseItem() == stack) return;
            MenuOpener.open(RailgunMenu.TYPE, p, MenuLocators.forHand(p, pkt.hand()));
        });
    }
}
