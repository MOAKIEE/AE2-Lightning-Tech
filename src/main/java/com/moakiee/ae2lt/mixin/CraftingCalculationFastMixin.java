package com.moakiee.ae2lt.mixin;

import java.util.IdentityHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;

import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingCalculation;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingTreeNode;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingTreeProcess;
import com.moakiee.ae2lt.logic.timewheelcpu.FastPlanningDecision;
import com.moakiee.ae2lt.logic.timewheelcpu.FastPlanningStats;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationFastMixin implements FastCraftingCalculation {
    @Unique
    private static final Logger AE2LT_FAST_LOG = LoggerFactory.getLogger("ae2lt-fast-crafting");

    @Unique
    private boolean ae2lt$fastPlanningEnabled;

    @Unique
    private final FastPlanningStats ae2lt$fastPlanningStats = new FastPlanningStats();

    @Shadow
    abstract void handlePausing() throws InterruptedException;

    @Shadow
    abstract void addMissing(AEKey what, long amount);

    @Shadow
    public abstract boolean isSimulation();

    @Override
    public boolean ae2lt$isFastPlanningEnabled() {
        return ae2lt$fastPlanningEnabled;
    }

    @Override
    public void ae2lt$setFastPlanningEnabled(boolean enabled) {
        this.ae2lt$fastPlanningEnabled = enabled;
    }

    @Override
    public void ae2lt$handlePausing() throws InterruptedException {
        this.handlePausing();
    }

    @Override
    public void ae2lt$addMissing(AEKey what, long amount) {
        this.addMissing(what, amount);
    }

    @Override
    public FastPlanningStats ae2lt$getFastPlanningStats() {
        return this.ae2lt$fastPlanningStats;
    }

    @Redirect(
            method = "runCraftAttempt",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/CraftingTreeNode;request(Lappeng/crafting/inv/CraftingSimulationState;JLappeng/api/stacks/KeyCounter;)V"))
    private void ae2lt$requestWithFastPlanning(CraftingTreeNode tree,
                                               CraftingSimulationState inv,
                                               long amount,
                                               KeyCounter containerItems)
            throws CraftBranchFailure, InterruptedException {
        var fastTree = (FastCraftingTreeNode) tree;
        if (!FastPlanningDecision.useFastAttempt(ae2lt$fastPlanningEnabled, this.isSimulation())) {
            fastTree.ae2lt$legacyRequest(inv, amount, containerItems);
            return;
        }

        boolean fallback = false;
        var processStates = new IdentityHashMap<FastCraftingTreeProcess, Boolean>();
        fastTree.ae2lt$captureFastProcessStates(processStates);
        this.ae2lt$fastPlanningStats.reset(System.nanoTime());
        try {
            var child = new ChildCraftingSimulationState(inv);
            fastTree.ae2lt$fastRequest(child, amount, containerItems);
            child.applyDiff(inv);
        } catch (CraftBranchFailure fastFailure) {
            fallback = true;
            this.ae2lt$fastPlanningStats.recordFallback();
            fastTree.ae2lt$restoreFastProcessStates(processStates);
            fastTree.ae2lt$legacyRequest(inv, amount, containerItems);
        } finally {
            this.ae2lt$fastPlanningStats.finish(System.nanoTime());
            if (AE2LT_FAST_LOG.isDebugEnabled()) {
                AE2LT_FAST_LOG.debug(
                        "[ae2lt] FAST crafting planning {}",
                        this.ae2lt$fastPlanningStats.summary(amount, fallback));
            }
        }
    }
}
