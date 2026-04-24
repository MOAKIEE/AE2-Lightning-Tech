package com.moakiee.ae2lt.logic.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

/**
 * High-level helpers for wireless FE distribution through Applied Flux.
 *
 * Applied Flux's {@code EnergyCapCache} expects to be constructed with a
 * "host" block position and then resolves the actual target via
 * {@code pos.relative(side)} (using {@code side.getOpposite()} as the cap
 * side context). Since our wireless connections store the target block's own
 * position and the face the player clicked, we emulate a virtual host sitting
 * one block beyond the target in the direction of the clicked face.
 */
public final class WirelessEnergyAPI {

    private WirelessEnergyAPI() {}

    public record Target(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        public Target {
            pos = pos.immutable();
        }

        /** Position of the virtual accessor that sits next to the target block. */
        public BlockPos virtualHostPos() {
            return pos.relative(face);
        }

        /** Direction from the virtual host toward the target block. */
        public Direction hostSide() {
            return face.getOpposite();
        }
    }

    private record ResolvedTarget(ServerLevel level, Target target) {
    }

    @Nullable
    public static ServerLevel resolveLevel(MinecraftServer server, Target target) {
        ServerLevel level = server.getLevel(target.dimension());
        if (level == null || !level.isLoaded(target.pos())) {
            return null;
        }
        return level;
    }

    @Nullable
    public static Object resolveCapCache(ServerLevel providerLevel, Target target, Supplier<IGrid> gridSupplier) {
        ServerLevel targetLevel = resolveLevel(providerLevel.getServer(), target);
        return targetLevel != null
                ? AppFluxBridge.createCapCache(targetLevel, target.virtualHostPos(), gridSupplier)
                : null;
    }

    /**
     * Pushes one AppFlux {@code send()} call to the given target.
     *
     * @param targetFace the face of the target block the user clicked; the
     *                   opposite direction is fed to AppFlux so it resolves
     *                   back to the target block.
     */
    public static long send(@Nullable Object capCache, Direction targetFace,
                            BufferedStorageService proxy, IActionSource source) {
        return AppFluxBridge.send(capCache, targetFace.getOpposite(), proxy, source);
    }

    public static long sendMulti(@Nullable Object capCache, Direction targetFace,
                                 BufferedStorageService proxy, IActionSource source,
                                 int maxCalls) {
        long total = 0L;
        Direction sideFromHost = targetFace.getOpposite();
        for (int i = 0; i < maxCalls; i++) {
            long pushed = AppFluxBridge.send(capCache, sideFromHost, proxy, source);
            if (pushed <= 0L) {
                break;
            }
            total += pushed;
        }
        return total;
    }

    public static long distributeBatch(
            ServerLevel providerLevel,
            List<Target> targets,
            BufferedMEStorage buffered,
            BufferedStorageService proxy,
            Supplier<IGrid> gridSupplier,
            IActionSource source) {
        if (!AppFluxBridge.canUseEnergyHandler() || AppFluxBridge.FE_KEY == null || targets.isEmpty()) {
            return 0L;
        }

        List<ResolvedTarget> liveTargets = new ArrayList<>(targets.size());
        for (Target target : targets) {
            ServerLevel level = resolveLevel(providerLevel.getServer(), target);
            if (level != null) {
                liveTargets.add(new ResolvedTarget(level, target));
            }
        }

        if (liveTargets.isEmpty()) {
            return 0L;
        }

        buffered.setCostMultiplier(1);
        buffered.preload(AppFluxBridge.FE_KEY,
                saturatingMul(AppFluxBridge.TRANSFER_RATE, liveTargets.size()), source);

        long totalPushed = 0L;
        try {
            for (ResolvedTarget entry : liveTargets) {
                Object capCache = AppFluxBridge.createCapCache(
                        entry.level(), entry.target().virtualHostPos(), gridSupplier);
                totalPushed += send(capCache, entry.target().face(), proxy, source);
            }
        } finally {
            buffered.flush(AppFluxBridge.FE_KEY, source);
        }

        return totalPushed;
    }

    /** Saturating multiply: 溢出时 clamp 到 Long.MAX_VALUE(TRANSFER_RATE=unlimited 哨兵保护) */
    private static long saturatingMul(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }
}
