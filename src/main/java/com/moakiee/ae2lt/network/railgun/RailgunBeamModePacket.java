package com.moakiee.ae2lt.network.railgun;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Client to server: switch the beam ammunition mode (HV / EHV) for the railgun
 * in the given hand. Triggered by Shift + mouse-wheel while a railgun is held.
 *
 * <p>The server is the source of truth; the client request is ignored if the
 * stack in the given hand is not a railgun. The selected mode is persisted on
 * {@link RailgunSettings} (and therefore the stack's DataComponents), so it
 * round-trips through pick-up / drop / shulker / hotbar swap unchanged.
 */
public record RailgunBeamModePacket(int modeOrdinal, InteractionHand hand) implements CustomPacketPayload {

    public static final Type<RailgunBeamModePacket> TYPE =
            new Type<>(NetworkInit.id("railgun_beam_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunBeamModePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RailgunBeamModePacket::modeOrdinal,
                    ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], InteractionHand::ordinal),
                    RailgunBeamModePacket::hand,
                    RailgunBeamModePacket::new);

    @Override
    public Type<RailgunBeamModePacket> type() {
        return TYPE;
    }

    public static void handle(RailgunBeamModePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            ItemStack stack = p.getItemInHand(pkt.hand());
            if (!(stack.getItem() instanceof ElectromagneticRailgunItem)) return;

            RailgunSettings.BeamMode newMode = RailgunSettings.BeamMode.byOrdinal(pkt.modeOrdinal());
            RailgunSettings current = stack.getOrDefault(
                    ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
            if (current.beamMode() == newMode) return;
            stack.set(ModDataComponents.RAILGUN_SETTINGS.get(), current.withBeamMode(newMode));
        });
    }
}
