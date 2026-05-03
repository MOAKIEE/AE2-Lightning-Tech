package com.moakiee.ae2lt.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Lightweight server-side packet dispatch helpers (railgun specific). */
public final class NetworkHandler {

    private NetworkHandler() {}

    /** Send to all players tracking the given chunk in the given level. */
    public static void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload) {
        var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunkPos.toLong());
        if (holder == null) {
            // Fall back to nearby tracking
            for (ServerPlayer p : level.players()) {
                if (Math.abs(p.chunkPosition().x - chunkPos.x) <= 8
                        && Math.abs(p.chunkPosition().z - chunkPos.z) <= 8) {
                    PacketDistributor.sendToPlayer(p, payload);
                }
            }
            return;
        }
        // Simpler: iterate players in level near the chunk.
        int cx = chunkPos.x;
        int cz = chunkPos.z;
        for (ServerPlayer p : level.players()) {
            int dx = p.chunkPosition().x - cx;
            int dz = p.chunkPosition().z - cz;
            if (dx * dx + dz * dz <= 8 * 8) {
                PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }
}
