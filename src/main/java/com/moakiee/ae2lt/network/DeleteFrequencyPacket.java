package com.moakiee.ae2lt.network;

import java.util.function.Supplier;

import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class DeleteFrequencyPacket {
    private final int token;
    private final int frequencyId;

    public DeleteFrequencyPacket(int token, int frequencyId) {
        this.token = token;
        this.frequencyId = frequencyId;
    }

    public int token() { return token; }
    public int frequencyId() { return frequencyId; }

    public static void encode(DeleteFrequencyPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.token);
        buf.writeInt(pkt.frequencyId);
    }

    public static DeleteFrequencyPacket decode(FriendlyByteBuf buf) {
        return new DeleteFrequencyPacket(buf.readVarInt(), buf.readInt());
    }

    public static void handle(DeleteFrequencyPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (FrequencyMenu.validateToken(player, pkt.token) == null) {
                NetworkInit.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }
            var manager = WirelessFrequencyManager.get();
            if (manager == null) return;

            WirelessFrequency freq = manager.getFrequency(pkt.frequencyId);
            if (freq == null) {
                NetworkInit.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FrequencyResponsePacket(FrequencyResponsePacket.INVALID_FREQUENCY));
                return;
            }

            // Multi-owner rule: any OWNER can delete the frequency —
            // we check isOwner() rather than the legacy single
            // ownerUUID equality, which would have gated deletion to
            // only the original creator.
            if (!freq.getPlayerAccess(player).isOwner()) {
                NetworkInit.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FrequencyResponsePacket(FrequencyResponsePacket.NO_PERMISSION));
                return;
            }

            manager.deleteFrequency(pkt.frequencyId, player.getServer());
            UpdateFrequencyBasicPacket.broadcastToOpenMenus(
                    player.getServer(), UpdateFrequencyBasicPacket.forDeletion(pkt.frequencyId));
        });
        ctx.setPacketHandled(true);
    }
}
