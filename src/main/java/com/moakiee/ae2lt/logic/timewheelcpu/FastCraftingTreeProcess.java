package com.moakiee.ae2lt.logic.timewheelcpu;

import java.util.IdentityHashMap;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.inv.CraftingSimulationState;

public interface FastCraftingTreeProcess {
    void ae2lt$fastRequest(CraftingSimulationState inv, long times)
            throws CraftBranchFailure, InterruptedException;

    void ae2lt$legacyRequest(CraftingSimulationState inv, long times)
            throws CraftBranchFailure, InterruptedException;

    long ae2lt$getOutputCount(AEKey what);

    boolean ae2lt$limitsQuantity();

    boolean ae2lt$isPossible();

    void ae2lt$setPossible(boolean possible);

    IPatternDetails ae2lt$getDetails();

    void ae2lt$captureFastProcessStates(IdentityHashMap<FastCraftingTreeProcess, Boolean> states);

    void ae2lt$restoreFastProcessStates(IdentityHashMap<FastCraftingTreeProcess, Boolean> states);
}
