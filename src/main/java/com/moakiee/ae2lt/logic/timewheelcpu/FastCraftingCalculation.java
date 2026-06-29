package com.moakiee.ae2lt.logic.timewheelcpu;

public interface FastCraftingCalculation {
    boolean ae2lt$isFastPlanningEnabled();

    void ae2lt$setFastPlanningEnabled(boolean enabled);

    void ae2lt$handlePausing() throws InterruptedException;

    FastPlanningStats ae2lt$getFastPlanningStats();
}
