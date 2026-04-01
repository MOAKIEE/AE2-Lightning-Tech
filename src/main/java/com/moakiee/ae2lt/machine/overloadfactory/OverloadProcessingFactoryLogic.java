package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;

import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingLockedRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCandidate;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeService;

public final class OverloadProcessingFactoryLogic implements IGridTickable {
    public static final int MIN_PROCESS_TICKS = 20;
    public static final long BASE_MAX_ENERGY_PER_TICK = 20_000L;

    @Nullable
    private static final AEKey CACHED_APPFLUX_FE_KEY;

    static {
        AEKey resolvedKey = null;
        try {
            var energyTypeClass = Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) energyTypeClass.asSubclass(Enum.class);
            Object feType = Enum.valueOf(enumClass, "FE");

            var fluxKeyClass = Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey");
            var ofMethod = fluxKeyClass.getMethod("of", energyTypeClass);
            Object key = ofMethod.invoke(null, feType);
            resolvedKey = key instanceof AEKey aeKey ? aeKey : null;
        } catch (ReflectiveOperationException ignored) {
        }
        CACHED_APPFLUX_FE_KEY = resolvedKey;
    }

    private final OverloadProcessingFactoryBlockEntity host;

    public OverloadProcessingFactoryLogic(OverloadProcessingFactoryBlockEntity host) {
        this.host = host;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, !hasGridTickWork());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (host.isRemoved() || host.getLevel() == null || host.isClientSide()) {
            return TickRateModulation.SLEEP;
        }

        rechargeFromAppliedFlux();

        if (!host.hasLockedRecipe()) {
            tryStartProcessing();
        }

        Optional<OverloadProcessingLockedRecipe> lockedRecipe = host.getLockedRecipe();
        if (lockedRecipe.isEmpty()) {
            host.setWorking(false);
            return TickRateModulation.SLEEP;
        }

        host.setWorking(true);

        Optional<OverloadProcessingRecipeCandidate> lockedCandidate = validateLockedRecipe(lockedRecipe.get());
        if (lockedCandidate.isEmpty()) {
            host.abortProcessing();
            return TickRateModulation.SLEEP;
        }

        return tickActiveRecipe(lockedRecipe.get(), lockedCandidate.get());
    }

    public void onStateChanged() {
        host.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    public boolean hasGridTickWork() {
        if (host.hasLockedRecipe()) {
            return true;
        }
        if (host.getInstalledMatrixCount() <= 0) {
            return false;
        }
        return host.findProcessableRecipe().isPresent();
    }

    public long getCurrentMaxEnergyPerTick() {
        int speedCards = 0;
        if (host instanceof IUpgradeableObject upgradeableObject) {
            speedCards = upgradeableObject.getInstalledUpgrades(AEItems.SPEED_CARD);
        }

        return switch (speedCards) {
            case 0 -> 20_000L;
            case 1 -> 100_000L;
            case 2 -> 400_000L;
            case 3 -> 1_600_000L;
            default -> 6_400_000L;
        };
    }

    public long getMinDurationLimitedMaxEnergyPerTick(long remainingEnergy, int processingTicksSpent) {
        int remainingRequiredTicks = Math.max(1, MIN_PROCESS_TICKS - processingTicksSpent);
        return divideCeil(remainingEnergy, remainingRequiredTicks);
    }

    public long computeEnergyToConsumeThisTick(OverloadProcessingLockedRecipe lockedRecipe) {
        long remainingEnergy = lockedRecipe.totalEnergy() - host.getConsumedEnergy();
        if (remainingEnergy <= 0L) {
            return 0L;
        }

        long upgradedMachineCap = getCurrentMaxEnergyPerTick();
        long minDurationLimitedCap = getMinDurationLimitedMaxEnergyPerTick(
                remainingEnergy,
                host.getProcessingTicksSpent());
        long availableFe = host.getEnergyStorage().getStoredEnergyLong();

        return Math.min(
                Math.min(upgradedMachineCap, minDurationLimitedCap),
                Math.min(availableFe, remainingEnergy));
    }

    private TickRateModulation tickActiveRecipe(
            OverloadProcessingLockedRecipe lockedRecipe,
            OverloadProcessingRecipeCandidate lockedCandidate) {
        if (host.getConsumedEnergy() >= lockedRecipe.totalEnergy()) {
            completeRecipe(lockedCandidate);
            return host.hasLockedRecipe() ? TickRateModulation.SLOWER : TickRateModulation.URGENT;
        }

        long toConsume = computeEnergyToConsumeThisTick(lockedRecipe);
        if (toConsume <= 0L) {
            return TickRateModulation.SLEEP;
        }

        int consumed = host.getEnergyStorage().extractInternal(toConsume, false);
        if (consumed <= 0) {
            return TickRateModulation.SLEEP;
        }

        host.addConsumedEnergy(consumed);
        host.incrementProcessingTicksSpent();

        if (host.getConsumedEnergy() >= lockedRecipe.totalEnergy()) {
            completeRecipe(lockedCandidate);
            return host.hasLockedRecipe() ? TickRateModulation.SLOWER : TickRateModulation.URGENT;
        }

        return TickRateModulation.URGENT;
    }

    private void tryStartProcessing() {
        Optional<OverloadProcessingLockedRecipe> lockedRecipe = host.lockCurrentRecipe();
        if (lockedRecipe.isEmpty()) {
            host.resetProgressState();
            host.setWorking(false);
            return;
        }

        host.resetProgressState();
        host.setWorking(true);
    }

    private Optional<OverloadProcessingRecipeCandidate> validateLockedRecipe(OverloadProcessingLockedRecipe lockedRecipe) {
        return OverloadProcessingRecipeService.findLockedRecipeMatch(
                host.getLevel(),
                host.getInventory(),
                host.getInputFluid(),
                host.getOutputFluid(),
                lockedRecipe,
                host.getAvailableHighVoltage(),
                host.getAvailableExtremeHighVoltage());
    }

    private void completeRecipe(OverloadProcessingRecipeCandidate lockedCandidate) {
        var lockedRecipe = host.getLockedRecipe().orElse(null);
        if (lockedRecipe == null || !host.completeLockedRecipe(lockedRecipe, lockedCandidate)) {
            host.abortProcessing();
        }
    }

    private void rechargeFromAppliedFlux() {
        if (CACHED_APPFLUX_FE_KEY == null) {
            return;
        }

        long missing = host.getEnergyStorage().getCapacityLong() - host.getEnergyStorage().getStoredEnergyLong();
        if (missing <= 0L) {
            return;
        }

        host.getMainNode().ifPresent((grid, node) -> {
            long extracted = grid.getStorageService().getInventory()
                    .extract(
                            CACHED_APPFLUX_FE_KEY,
                            Math.min(missing, Integer.MAX_VALUE),
                            Actionable.MODULATE,
                            IActionSource.ofMachine(host));
            if (extracted <= 0L) {
                return;
            }

            int inserted = host.getEnergyStorage().receiveEnergy((int) Math.min(extracted, Integer.MAX_VALUE), false);
            long remainder = extracted - inserted;
            if (remainder > 0L) {
                grid.getStorageService().getInventory()
                        .insert(CACHED_APPFLUX_FE_KEY, remainder, Actionable.MODULATE, IActionSource.ofMachine(host));
            }
        });
    }

    private static long divideCeil(long dividend, long divisor) {
        if (divisor <= 0L) {
            throw new IllegalArgumentException("divisor must be positive");
        }
        if (dividend <= 0L) {
            return 0L;
        }
        return (dividend + divisor - 1L) / divisor;
    }
}
