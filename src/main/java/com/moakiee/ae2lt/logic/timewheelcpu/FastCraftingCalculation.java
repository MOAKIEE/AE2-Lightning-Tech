package com.moakiee.ae2lt.logic.timewheelcpu;

import appeng.api.stacks.AEKey;

public interface FastCraftingCalculation {
    boolean ae2lt$isFastPlanningEnabled();

    void ae2lt$setFastPlanningEnabled(boolean enabled);

    void ae2lt$handlePausing() throws InterruptedException;

    void ae2lt$addMissing(AEKey what, long amount);

    FastPlanningStats ae2lt$getFastPlanningStats();
}
