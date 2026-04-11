package com.moakiee.ae2lt.machine.teslacoil;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;

import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.logic.AppFluxHelper;

public final class TeslaCoilLogic implements IGridTickable {
    private final TeslaCoilBlockEntity host;

    public TeslaCoilLogic(TeslaCoilBlockEntity host) {
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

        if (!host.hasLockedMode()) {
            if (!host.hasLocalStartPrerequisites()) {
                host.setWorking(false);
                return TickRateModulation.SLEEP;
            }

            if (host.canStartSelectedMode() && host.hasEnoughEnergyForSelectedStart()) {
                if (!host.lockSelectedMode()) {
                    host.setWorking(false);
                    return TickRateModulation.SLOWER;
                }
            } else {
                host.setWorking(false);
                return TickRateModulation.SLOWER;
            }
        }

        host.setWorking(true);

        if (host.isReadyToCommit()) {
            if (host.commitLockedMode()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }

        if (!host.hasLockedModeLocalPrerequisites()) {
            return TickRateModulation.SLOWER;
        }

        long required = host.getRequiredEnergyForNextTick();
        if (required <= 0L) {
            return TickRateModulation.SLOWER;
        }

        if (host.getEnergyStorage().getStoredEnergyLong() < required) {
            return TickRateModulation.SLOWER;
        }

        int extracted = host.getEnergyStorage().extractInternal(required, false);
        if (extracted < required) {
            return TickRateModulation.SLOWER;
        }

        host.advanceProgress(extracted);

        if (host.isReadyToCommit() && host.commitLockedMode()) {
            return TickRateModulation.URGENT;
        }

        return TickRateModulation.URGENT;
    }

    public boolean hasGridTickWork() {
        return host.hasLockedMode() || host.hasLocalStartPrerequisites();
    }

    public void onStateChanged() {
        host.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private void rechargeFromAppliedFlux() {
        if (!AppFluxHelper.isAvailable()) {
            return;
        }

        host.getMainNode().ifPresent((grid, node) -> {
            AppFluxHelper.pullPowerFromNetwork(
                    grid.getStorageService().getInventory(),
                    host.getEnergyStorage(),
                    appeng.api.networking.security.IActionSource.ofMachine(host));
        });
    }
}
