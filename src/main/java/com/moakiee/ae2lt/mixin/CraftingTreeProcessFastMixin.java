package com.moakiee.ae2lt.mixin;

import java.util.IdentityHashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;

import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingCalculation;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingTreeNode;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingTreeProcess;

@Mixin(value = CraftingTreeProcess.class, remap = false)
public abstract class CraftingTreeProcessFastMixin implements FastCraftingTreeProcess {
    @Shadow
    @Final
    private CraftingCalculation job;

    @Shadow
    private boolean containerItems;

    @Shadow
    @Final
    private Map<CraftingTreeNode, Long> nodes;

    @Shadow
    @Final
    IPatternDetails details;

    @Shadow
    boolean possible;

    @Shadow
    private boolean limitQty;

    @Shadow
    abstract void request(CraftingSimulationState inv, long times)
            throws CraftBranchFailure, InterruptedException;

    @Override
    public void ae2lt$fastRequest(CraftingSimulationState inv, long times)
            throws CraftBranchFailure, InterruptedException {
        ((FastCraftingCalculation) this.job).ae2lt$handlePausing();

        var containerItems = this.containerItems ? new KeyCounter() : null;

        for (var entry : this.nodes.entrySet()) {
            ((FastCraftingTreeNode) entry.getKey()).ae2lt$fastRequest(
                    inv,
                    entry.getValue() * times,
                    containerItems);
        }

        ae2lt$onSucceeded(inv, times, containerItems);
    }

    @Override
    public void ae2lt$legacyRequest(CraftingSimulationState inv, long times)
            throws CraftBranchFailure, InterruptedException {
        this.request(inv, times);
    }

    @Unique
    private void ae2lt$onSucceeded(CraftingSimulationState inv, long times, KeyCounter containerItems) {
        if (containerItems != null) {
            for (var stack : containerItems) {
                inv.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
                inv.addStackBytes(stack.getKey(), stack.getLongValue(), 1);
            }
        }

        for (var out : this.details.getOutputs()) {
            inv.insert(out.what(), out.amount() * times, Actionable.MODULATE);
        }

        inv.addCrafting(details, times);
        inv.addBytes(times);
    }

    @Override
    public long ae2lt$getOutputCount(AEKey what) {
        long total = 0;
        for (var output : this.details.getOutputs()) {
            if (what.matches(output)) {
                total += output.amount();
            }
        }
        return total;
    }

    @Override
    public boolean ae2lt$limitsQuantity() {
        return this.limitQty;
    }

    @Override
    public boolean ae2lt$isPossible() {
        return this.possible;
    }

    @Override
    public void ae2lt$setPossible(boolean possible) {
        this.possible = possible;
    }

    @Override
    public IPatternDetails ae2lt$getDetails() {
        return this.details;
    }

    @Override
    public void ae2lt$captureFastProcessStates(IdentityHashMap<FastCraftingTreeProcess, Boolean> states) {
        var self = (FastCraftingTreeProcess) (Object) this;
        if (states.putIfAbsent(self, this.possible) != null) {
            return;
        }

        for (var node : this.nodes.keySet()) {
            ((FastCraftingTreeNode) node).ae2lt$captureFastProcessStates(states);
        }
    }

    @Override
    public void ae2lt$restoreFastProcessStates(IdentityHashMap<FastCraftingTreeProcess, Boolean> states) {
        var self = (FastCraftingTreeProcess) (Object) this;
        var possible = states.get(self);
        this.possible = possible == null || possible;

        for (var node : this.nodes.keySet()) {
            ((FastCraftingTreeNode) node).ae2lt$restoreFastProcessStates(states);
        }
    }
}
