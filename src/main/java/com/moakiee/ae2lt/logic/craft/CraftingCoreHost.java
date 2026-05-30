package com.moakiee.ae2lt.logic.craft;

import appeng.api.stacks.AEKey;

public interface CraftingCoreHost {
    long getGameTime();

    int maxThreads();

    boolean isRemoved();

    boolean isConnected();

    double extractEnergy(double amount);

    void injectEnergy(double amount);

    long insertToNetwork(AEKey key, long amount);

    void spawnToWorld(AEKey key, long amount);
}
