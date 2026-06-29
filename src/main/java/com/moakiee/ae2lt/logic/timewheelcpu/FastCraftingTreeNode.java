package com.moakiee.ae2lt.logic.timewheelcpu;

import java.util.IdentityHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.inv.CraftingSimulationState;

public interface FastCraftingTreeNode {
    void ae2lt$legacyRequest(CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    void ae2lt$fastRequest(CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    void ae2lt$captureFastProcessStates(IdentityHashMap<FastCraftingTreeProcess, Boolean> states);

    void ae2lt$restoreFastProcessStates(IdentityHashMap<FastCraftingTreeProcess, Boolean> states);
}
