package com.moakiee.ae2lt.machine.common;

import java.util.Optional;

public interface GridRecipeMachineHost<L, C> {
    boolean hasLockedRecipe();

    Optional<L> getLockedRecipe();

    Optional<L> lockCurrentRecipe();

    void resetProgressState();

    void setWorking(boolean working);

    boolean pushOutResult();

    boolean hasAutoExportWork();

    void abortProcessing();

    long getConsumedEnergy();

    int getProcessingTicksSpent();

    boolean hasProcessableRecipe();

    boolean completeLockedRecipe(L lockedRecipe, C candidate);

    long getMachineStoredEnergy();

    long getMachineEnergyCapacity();

    int extractMachineEnergy(long amount);

    int receiveMachineEnergy(int amount);

    void onEnergyConsumed(int consumed);
}
