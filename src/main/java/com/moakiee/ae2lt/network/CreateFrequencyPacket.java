package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CreateFrequencyPacket(
        int token,
        String name, int color,
        FrequencySecurityLevel security, String password
) implements CustomPacketPayload {

    public static final Type<CreateFrequencyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "create_frequency"));

    public static final StreamCodec<FriendlyByteBuf, CreateFrequencyPacket> STREAM_CODEC =
            StreamCodec.of(CreateFrequencyPacket::encode, CreateFrequencyPacket::decode);

    private static void encode(FriendlyByteBuf buf, CreateFrequencyPacket pkt) {
        buf.writeVarInt(pkt.token);
        buf.writeUtf(pkt.name, WirelessFrequency.MAX_NAME_LENGTH);
        buf.writeInt(pkt.color);
        buf.writeByte(pkt.security.getId());
        buf.writeUtf(pkt.password, WirelessFrequency.MAX_PASSWORD_LENGTH);
    }

    private static CreateFrequencyPacket decode(FriendlyByteBuf buf) {
        return new CreateFrequencyPacket(
                buf.readVarInt(),
                buf.readUtf(WirelessFrequency.MAX_NAME_LENGTH),
                buf.readInt(),
                FrequencySecurityLevel.fromId(buf.readByte()),
                buf.readUtf(WirelessFrequency.MAX_PASSWORD_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreateFrequencyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (FrequencyMenu.validateToken(player, pkt.token) == null) {
                PacketDistributor.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }
            var manager = WirelessFrequencyManager.get();
            if (manager == null) return;

            if (pkt.name.isBlank()) {
                PacketDistributor.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }
            if (pkt.security == FrequencySecurityLevel.ENCRYPTED && pkt.password.isBlank()) {
                PacketDistributor.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.REQUIRE_PASSWORD));
                return;
            }

            var freq = manager.createFrequency(player, pkt.name, pkt.color, pkt.security, pkt.password);
            if (freq != null) {
                UpdateFrequencyBasicPacket.broadcastToOpenMenus(
                        player.getServer(), UpdateFrequencyBasicPacket.forFrequency(freq));
            }
        });
    }
}
