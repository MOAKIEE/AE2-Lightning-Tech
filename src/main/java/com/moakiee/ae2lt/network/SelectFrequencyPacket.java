package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectFrequencyPacket(
        int token,
        BlockPos blockPos, int frequencyId, String password
) implements CustomPacketPayload {

    public static final Type<SelectFrequencyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "select_frequency"));

    public static final StreamCodec<FriendlyByteBuf, SelectFrequencyPacket> STREAM_CODEC =
            StreamCodec.of(SelectFrequencyPacket::encode, SelectFrequencyPacket::decode);

    private static void encode(FriendlyByteBuf buf, SelectFrequencyPacket pkt) {
        buf.writeVarInt(pkt.token);
        buf.writeBlockPos(pkt.blockPos);
        buf.writeInt(pkt.frequencyId);
        buf.writeUtf(pkt.password, WirelessFrequency.MAX_PASSWORD_LENGTH);
    }

    private static SelectFrequencyPacket decode(FriendlyByteBuf buf) {
        return new SelectFrequencyPacket(
                buf.readVarInt(),
                buf.readBlockPos(),
                buf.readInt(),
                buf.readUtf(WirelessFrequency.MAX_PASSWORD_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectFrequencyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            FrequencyMenu menu = FrequencyMenu.validateToken(player, pkt.token);
            if (menu == null || !menu.getBlockPos().equals(pkt.blockPos)) {
                PacketDistributor.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }

            var level = player.serverLevel();
            var be = level.getBlockEntity(pkt.blockPos);

            // disconnect
            if (pkt.frequencyId <= 0) {
                if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
                    ctrl.clearFrequency();
                } else if (be instanceof WirelessReceiverBlockEntity recv) {
                    recv.clearFrequency();
                } else {
                    PacketDistributor.sendToPlayer(player,
                            new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                }
                // DataSlot handles freqId sync back to client
                return;
            }

            var manager = WirelessFrequencyManager.get();
            if (manager == null) return;

            WirelessFrequency freq = manager.getFrequency(pkt.frequencyId);
            if (freq == null) {
                PacketDistributor.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.INVALID_FREQUENCY));
                return;
            }

            if (!freq.canPlayerAccess(player, pkt.password)) {
                if (freq.getSecurity() == FrequencySecurityLevel.ENCRYPTED
                        && !freq.getPlayerAccess(player).canUse()
                        && pkt.password.isBlank()) {
                    PacketDistributor.sendToPlayer(player,
                            new FrequencyResponsePacket(FrequencyResponsePacket.REQUIRE_PASSWORD));
                } else if (freq.getSecurity() == FrequencySecurityLevel.ENCRYPTED
                        && !freq.getPlayerAccess(player).canUse()) {
                    PacketDistributor.sendToPlayer(player,
                            new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                } else {
                    PacketDistributor.sendToPlayer(player,
                            new FrequencyResponsePacket(FrequencyResponsePacket.NO_PERMISSION));
                }
                return;
            }

            if (be instanceof WirelessOverloadedControllerBlockEntity
                    && !manager.canRegisterTransmitter(pkt.frequencyId, level.dimension(), pkt.blockPos)) {
                PacketDistributor.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.FREQUENCY_IN_USE));
                return;
            }

            if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
                ctrl.setFrequency(pkt.frequencyId);
            } else if (be instanceof WirelessReceiverBlockEntity recv) {
                recv.setFrequency(pkt.frequencyId);
            } else {
                PacketDistributor.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
            }
            // DataSlot handles freqId sync; members may have been updated above
        });
    }
}
