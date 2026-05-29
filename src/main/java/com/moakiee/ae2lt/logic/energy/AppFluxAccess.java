package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import com.glodblock.github.appflux.api.IFluxCell;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;

/**
 * Direct access to Applied Flux APIs for 1.20.1.
 * Only loaded when AppFlux is present at runtime.
 */
final class AppFluxAccess {
    private static final boolean LOADED = ModList.get().isLoaded("appflux");

    static final AEKey FE_KEY = LOADED ? FluxKey.of(EnergyType.FE) : null;
    static final long TRANSFER_RATE = LOADED ? 10000L : 0L;

    static boolean isFluxCell(ItemStack stack) {
        if (!LOADED) return false;
        return stack.getItem() instanceof IFluxCell;
    }

    @Nullable
    static Object createCapCache(ServerLevel level, BlockPos pos, Supplier<IGrid> gridSupplier) {
        if (!LOADED) return null;
        return new EnergyCapCache(level, pos);
    }

    @Nullable
    static TargetAccess resolveEnergyTarget(Object energyCapCache, Direction side) {
        if (!LOADED || !(energyCapCache instanceof EnergyCapCache cache)) return null;
        return cache.resolve(side);
    }

    static long simulateTarget(@Nullable TargetAccess target, long maxFe) {
        if (!LOADED || target == null) return 0L;
        return target.simulateReceive(maxFe);
    }

    static long sendToTarget(@Nullable TargetAccess target, IStorageService storage,
                             IActionSource source, long maxFe) {
        if (!LOADED || target == null || FE_KEY == null) return 0L;
        long extracted = storage.getInventory().extract(FE_KEY, maxFe, appeng.api.config.Actionable.SIMULATE, source);
        if (extracted <= 0) return 0L;
        long accepted = target.receive(extracted);
        if (accepted > 0) {
            storage.getInventory().extract(FE_KEY, accepted, appeng.api.config.Actionable.MODULATE, source);
        }
        return accepted;
    }

    static long sendToTargetKnownDemand(@Nullable TargetAccess target, IStorageService storage,
                                        IActionSource source, long requested) {
        if (!LOADED || target == null || FE_KEY == null) return 0L;
        long extracted = storage.getInventory().extract(FE_KEY, requested, appeng.api.config.Actionable.SIMULATE, source);
        if (extracted <= 0) return 0L;
        long accepted = target.receive(extracted);
        if (accepted > 0) {
            storage.getInventory().extract(FE_KEY, accepted, appeng.api.config.Actionable.MODULATE, source);
        }
        return accepted;
    }

    static long sendToTargetKnownDemand(@Nullable TargetAccess target, BufferedMEStorage buffer,
                                        IActionSource source, long requested) {
        if (!LOADED || target == null || FE_KEY == null) return 0L;
        long extracted = buffer.extract(FE_KEY, requested, appeng.api.config.Actionable.SIMULATE, source);
        if (extracted <= 0) return 0L;
        long accepted = target.receive(extracted);
        if (accepted > 0) {
            buffer.extract(FE_KEY, accepted, appeng.api.config.Actionable.MODULATE, source);
        }
        return accepted;
    }

    static long sendToTargetRepeatedOptimistic(@Nullable TargetAccess target, BufferedMEStorage buffer,
                                              IActionSource source, long maxFe, int maxCalls) {
        if (!LOADED || target == null || FE_KEY == null) return 0L;
        long totalSent = 0;
        for (int i = 0; i < maxCalls && totalSent < maxFe; i++) {
            long extracted = buffer.extract(FE_KEY, maxFe - totalSent, appeng.api.config.Actionable.SIMULATE, source);
            if (extracted <= 0) break;
            long accepted = target.receive(extracted);
            if (accepted <= 0) break;
            buffer.extract(FE_KEY, accepted, appeng.api.config.Actionable.MODULATE, source);
            totalSent += accepted;
        }
        return totalSent;
    }

    static long getFluxCellCapacity(ItemStack stack) {
        if (!LOADED) return 0L;
        if (stack.getItem() instanceof IFluxCell cell) {
            return cell.getBytes(stack);
        }
        return 0L;
    }

    static void persistCellStorage(@Nullable MEStorage storage) {
        // No-op in 1.20.1
    }

    private AppFluxAccess() {}

    private static class EnergyCapCache {
        private final ServerLevel level;
        private final BlockPos pos;

        EnergyCapCache(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Nullable
        TargetAccess resolve(Direction side) {
            var be = level.getBlockEntity(pos);
            if (be == null) return null;
            var cap = be.getCapability(ForgeCapabilities.ENERGY, side);
            return cap.map(FeTargetAccess::new).orElse(null);
        }
    }

    private static class FeTargetAccess implements TargetAccess {
        private final IEnergyStorage energy;

        FeTargetAccess(IEnergyStorage energy) {
            this.energy = energy;
        }

        @Override
        public long simulateReceive(long maxFe) {
            if (!energy.canReceive()) return 0L;
            int canReceive = energy.getMaxEnergyStored() - energy.getEnergyStored();
            return Math.min(maxFe, canReceive);
        }

        @Override
        public long receive(long amountFe) {
            if (!energy.canReceive()) return 0L;
            int toSend = (int) Math.min(amountFe, Integer.MAX_VALUE);
            return energy.receiveEnergy(toSend, false);
        }
    }
}
