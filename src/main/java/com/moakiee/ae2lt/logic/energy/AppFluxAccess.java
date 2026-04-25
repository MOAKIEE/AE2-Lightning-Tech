package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;

import com.glodblock.github.appflux.api.IFluxCell;
import com.glodblock.github.appflux.common.me.cell.FluxCellInventory;
import com.glodblock.github.appflux.common.me.energy.EnergyCapCache;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.glodblock.github.appflux.config.AFConfig;
import com.glodblock.github.appflux.xmod.fluxnetwork.FluxNetworkCap;
import com.glodblock.github.appflux.xmod.mek.MekEnergyCap;
import com.glodblock.github.appflux.xmod.mi.LongEnergyCap;

import dev.technici4n.grandpower.api.ILongEnergyStorage;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.util.UnitDisplayUtils;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;

/**
 * Direct access to Applied Flux APIs. This class is ONLY loaded when AppFlux
 * is present at runtime — the JVM's lazy class loading ensures that importing
 * AppFlux types here never triggers a {@link NoClassDefFoundError} as long as
 * callers guard access behind the {@code LOADED} check in
 * {@link AppFluxBridge}.
 */
final class AppFluxAccess {

    static final AEKey FE_KEY = FluxKey.of(EnergyType.FE);

    static final long TRANSFER_RATE;
    private static final boolean GRAND_POWER_LOADED;
    private static final boolean FLUX_NETWORKS_LOADED;
    private static final boolean MEKANISM_LOADED;

    static {
        long rate = AFConfig.getFluxAccessorIO();
        TRANSFER_RATE = rate == 0L ? Long.MAX_VALUE : Math.max(0L, rate);
        GRAND_POWER_LOADED = isClassPresent("dev.technici4n.grandpower.api.ILongEnergyStorage");
        FLUX_NETWORKS_LOADED = isClassPresent("sonar.fluxnetworks.api.energy.IFNEnergyStorage");
        MEKANISM_LOADED = isClassPresent("mekanism.api.energy.IStrictEnergyHandler");
    }

    static boolean isFluxCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IFluxCell;
    }

    @Nullable
    static Object createCapCache(ServerLevel level, BlockPos pos,
                                 Supplier<IGrid> gridSupplier) {
        return new EnergyCapCache(level, pos, gridSupplier);
    }

    @Nullable
    static Object resolveEnergyTarget(Object energyCapCache, Direction side) {
        if (!(energyCapCache instanceof EnergyCapCache cache)) {
            return null;
        }

        TargetAccess target = resolveGrandPowerTarget(cache, side);
        if (target != null) {
            return target;
        }
        target = resolveFluxNetworkTarget(cache, side);
        if (target != null) {
            return target;
        }
        target = resolveMekanismTarget(cache, side);
        if (target != null) {
            return target;
        }
        return ForgeEnergyTarget.resolve(cache, side);
    }

    static long simulateTarget(Object target, long maxFe) {
        return target instanceof TargetAccess access && maxFe > 0L
                ? Math.max(0L, access.simulateReceive(maxFe))
                : 0L;
    }

    static long sendToTarget(Object target, IStorageService storage,
                             IActionSource source, long maxFe) {
        if (!(target instanceof TargetAccess access) || maxFe <= 0L) {
            return 0L;
        }

        long requested = Math.max(0L, access.simulateReceive(maxFe));
        if (requested <= 0L) {
            return 0L;
        }

        long extracted = storage.getInventory().extract(
                FE_KEY, Math.min(requested, maxFe), Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }

        long accepted = Math.min(extracted, Math.max(0L, access.receive(extracted)));
        long leftover = extracted - accepted;
        if (leftover > 0L) {
            storage.getInventory().insert(FE_KEY, leftover, Actionable.MODULATE, source);
        }
        return accepted;
    }

    static long sendToTargetKnownDemand(Object target, IStorageService storage,
                                        IActionSource source, long requested) {
        if (!(target instanceof TargetAccess access) || requested <= 0L) {
            return 0L;
        }

        long extracted = storage.getInventory().extract(
                FE_KEY, requested, Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }

        long accepted = Math.min(extracted, Math.max(0L, access.receive(extracted)));
        long leftover = extracted - accepted;
        if (leftover > 0L) {
            storage.getInventory().insert(FE_KEY, leftover, Actionable.MODULATE, source);
        }
        return accepted;
    }

    static long sendToTargetOptimistic(Object target, BufferedMEStorage buffer,
                                       IActionSource source, long maxFe) {
        if (!(target instanceof TargetAccess access) || maxFe <= 0L) {
            return 0L;
        }

        long extracted = buffer.extractForDirectSend(maxFe, source);
        if (extracted <= 0L) {
            long currentDemand = Math.min(maxFe, Math.max(0L, access.simulateReceive(maxFe)));
            long refillDemand = buffer.refillBudgetForDirectSend(currentDemand);
            if (refillDemand <= 0L) {
                return 0L;
            }
            buffer.refillForDirectSend(refillDemand, source);
            extracted = buffer.extractForDirectSend(currentDemand, source);
        }
        if (extracted <= 0L) {
            return 0L;
        }

        long accepted = Math.min(extracted, Math.max(0L, access.receive(extracted)));
        long leftover = extracted - accepted;
        if (leftover > 0L) {
            buffer.returnFromDirectSend(leftover, source);
        }
        return accepted;
    }

    static long getFluxCellCapacity(ItemStack stack) {
        var inventory = StorageCells.getCellInventory(stack, null);
        if (inventory instanceof FluxCellInventory fluxInv) {
            return Math.max(0L, fluxInv.getMaxEnergy());
        }
        if (stack.getItem() instanceof IFluxCell fluxCell) {
            return Math.max(0L, fluxCell.getBytes(stack));
        }
        return 0L;
    }

    static void persistCellStorage(@Nullable MEStorage storage) {
        if (storage instanceof FluxCellInventory fluxInv) {
            fluxInv.persist();
        }
    }

    private AppFluxAccess() {}

    private interface TargetAccess {
        long simulateReceive(long maxFe);

        long receive(long amountFe);
    }

    @Nullable
    private static TargetAccess resolveGrandPowerTarget(EnergyCapCache cache, Direction side) {
        if (!GRAND_POWER_LOADED) {
            return null;
        }
        try {
            ILongEnergyStorage target = cache.getEnergyCap(LongEnergyCap.CAP, side);
            return target != null ? new GrandPowerTarget(target) : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static TargetAccess resolveFluxNetworkTarget(EnergyCapCache cache, Direction side) {
        if (!FLUX_NETWORKS_LOADED) {
            return null;
        }
        try {
            IFNEnergyStorage target = cache.getEnergyCap(FluxNetworkCap.CAP, side);
            return target != null ? new FluxNetworkTarget(target) : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static TargetAccess resolveMekanismTarget(EnergyCapCache cache, Direction side) {
        if (!MEKANISM_LOADED) {
            return null;
        }
        try {
            IStrictEnergyHandler target = cache.getEnergyCap(MekEnergyCap.CAP, side);
            return target != null ? new MekanismStrictTarget(target) : null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private record GrandPowerTarget(ILongEnergyStorage target) implements TargetAccess {
        @Override
        public long simulateReceive(long maxFe) {
            return Math.max(0L, target.receive(maxFe, true));
        }

        @Override
        public long receive(long amountFe) {
            return Math.max(0L, target.receive(amountFe, false));
        }
    }

    private record FluxNetworkTarget(IFNEnergyStorage target) implements TargetAccess {
        @Override
        public long simulateReceive(long maxFe) {
            return Math.max(0L, target.receiveEnergyL(maxFe, true));
        }

        @Override
        public long receive(long amountFe) {
            return Math.max(0L, target.receiveEnergyL(amountFe, false));
        }
    }

    private record MekanismStrictTarget(IStrictEnergyHandler target) implements TargetAccess {

        @Override
        public long simulateReceive(long maxFe) {
            return insert(maxFe, Action.SIMULATE);
        }

        @Override
        public long receive(long amountFe) {
            return insert(amountFe, Action.EXECUTE);
        }

        private long insert(long amountFe, Action action) {
            long mekanismAmount = UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(amountFe);
            if (mekanismAmount <= 0L) {
                return 0L;
            }
            long remainder = target.insertEnergy(mekanismAmount, action);
            long accepted = mekanismAmount - Math.max(0L, remainder);
            return accepted > 0L ? UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertTo(accepted) : 0L;
        }
    }

    private record ForgeEnergyTarget(IEnergyStorage target) implements TargetAccess {
        @Nullable
        static TargetAccess resolve(EnergyCapCache cache, Direction side) {
            IEnergyStorage target = cache.getEnergyCap(Capabilities.EnergyStorage.BLOCK, side);
            return target != null ? new ForgeEnergyTarget(target) : null;
        }

        @Override
        public long simulateReceive(long maxFe) {
            return Math.max(0, target.receiveEnergy(clampToInt(maxFe), true));
        }

        @Override
        public long receive(long amountFe) {
            return Math.max(0, target.receiveEnergy(clampToInt(amountFe), false));
        }
    }

    private static int clampToInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, AppFluxAccess.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
