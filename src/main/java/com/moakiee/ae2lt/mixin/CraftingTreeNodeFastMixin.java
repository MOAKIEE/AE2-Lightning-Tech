package com.moakiee.ae2lt.mixin;

import java.util.ArrayList;
import java.util.IdentityHashMap;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;

import com.moakiee.ae2lt.logic.timewheelcpu.FastBranchCrafting;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingCalculation;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingTreeNode;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingTreeProcess;
import com.moakiee.ae2lt.logic.timewheelcpu.FastPlanningDecision;
import com.moakiee.ae2lt.logic.timewheelcpu.FastPlanningStats;

@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeNodeFastMixin implements FastCraftingTreeNode {
    @Shadow
    @Final
    private CraftingCalculation job;

    @Shadow
    @Final
    private AEKey what;

    @Shadow
    @Final
    @Nullable
    private IPatternDetails.IInput parentInput;

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private long amount;

    @Shadow
    private ArrayList<CraftingTreeProcess> nodes;

    @Shadow
    @Final
    private boolean canEmit;

    @Shadow
    abstract void request(CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    @Shadow
    private void buildChildPatterns() {
        throw new AssertionError();
    }

    @Shadow
    private void addContainerItems(AEKey template, long multiplier, @Nullable KeyCounter outputList) {
        throw new AssertionError();
    }

    @Shadow
    private Iterable<InputTemplate> getValidItemTemplates(ICraftingInventory inv) {
        throw new AssertionError();
    }

    @Override
    public void ae2lt$legacyRequest(CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        this.request(inv, requestedAmount, containerItems);
    }

    @Override
    public void ae2lt$fastRequest(CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        var fastCalculation = (FastCraftingCalculation) this.job;
        var stats = fastCalculation.ae2lt$getFastPlanningStats();

        fastCalculation.ae2lt$handlePausing();

        if (this.canEmit) {
            stats.recordLegacyBranch();
            this.request(inv, requestedAmount, containerItems);
            return;
        }

        buildChildPatterns();
        int branchCount = this.nodes.size();
        stats.recordBranches(branchCount);

        if (FastPlanningDecision.useLegacyNode(branchCount)) {
            stats.recordLegacyBranch();
            this.request(inv, requestedAmount, containerItems);
            return;
        }

        inv.addStackBytes(what, amount, requestedAmount);

        requestedAmount = ae2lt$extractExactThenFallback(inv, requestedAmount, containerItems, stats);
        if (requestedAmount == 0) {
            return;
        }

        addContainerItems(what, requestedAmount, containerItems);

        long totalRequestedItems = requestedAmount * this.amount;
        totalRequestedItems = ae2lt$requestMultipleBranches(inv, totalRequestedItems);

        if (totalRequestedItems <= 0) {
            return;
        }

        throw new CraftBranchFailure(this.what, totalRequestedItems);
    }

    @Override
    public void ae2lt$captureFastProcessStates(IdentityHashMap<FastCraftingTreeProcess, Boolean> states) {
        if (this.nodes == null) {
            return;
        }

        for (var process : this.nodes) {
            ((FastCraftingTreeProcess) process).ae2lt$captureFastProcessStates(states);
        }
    }

    @Override
    public void ae2lt$restoreFastProcessStates(IdentityHashMap<FastCraftingTreeProcess, Boolean> states) {
        if (this.nodes == null) {
            return;
        }

        for (var process : this.nodes) {
            ((FastCraftingTreeProcess) process).ae2lt$restoreFastProcessStates(states);
        }
    }

    @Unique
    private long ae2lt$extractExactThenFallback(CraftingSimulationState inv,
                                                long requestedAmount,
                                                @Nullable KeyCounter containerItems,
                                                FastPlanningStats stats) {
        long exactExtracted = 0;
        if (this.parentInput == null || this.parentInput.isValid(this.what, this.level)) {
            var exactTemplate = new InputTemplate(this.what, this.amount);
            exactExtracted = CraftingCpuHelper.extractTemplates(inv, exactTemplate, requestedAmount);
            if (exactExtracted > 0) {
                requestedAmount -= exactExtracted;
                addContainerItems(this.what, exactExtracted, containerItems);

                if (requestedAmount == 0) {
                    return 0;
                }
            }
        }

        for (var template : getValidItemTemplates(inv)) {
            stats.recordFuzzyCandidate();
            if (exactExtracted > 0
                    && template.amount() == this.amount
                    && template.key().equals(this.what)) {
                continue;
            }

            long extracted = CraftingCpuHelper.extractTemplates(inv, template, requestedAmount);
            if (extracted > 0) {
                requestedAmount -= extracted;
                addContainerItems(template.key(), extracted, containerItems);

                if (requestedAmount == 0) {
                    return 0;
                }
            }
        }

        return requestedAmount;
    }

    @Unique
    private long ae2lt$requestMultipleBranches(CraftingSimulationState inv, long totalRequestedItems)
            throws InterruptedException {
        int branchCount = this.nodes.size();
        for (var node : this.nodes) {
            if (totalRequestedItems <= 0) {
                return 0;
            }

            var process = (FastCraftingTreeProcess) node;
            if (!process.ae2lt$isPossible()) {
                continue;
            }

            long available = FastPlanningDecision.useBulkBranch(branchCount, process.ae2lt$limitsQuantity())
                    ? ae2lt$requestBulkBranch(inv, process, totalRequestedItems)
                    : ae2lt$requestLegacyBranch(inv, process, totalRequestedItems);

            totalRequestedItems -= available;
        }
        return totalRequestedItems;
    }

    @Unique
    private long ae2lt$requestLegacyBranch(CraftingSimulationState inv,
                                           FastCraftingTreeProcess process,
                                           long totalRequestedItems)
            throws InterruptedException {
        ((FastCraftingCalculation) this.job).ae2lt$getFastPlanningStats().recordLegacyBranch();

        long crafted = 0;
        while (process.ae2lt$isPossible() && totalRequestedItems > crafted) {
            var child = new ChildCraftingSimulationState(inv);
            try {
                process.ae2lt$legacyRequest(child, 1);
            } catch (CraftBranchFailure fail) {
                ((FastCraftingCalculation) this.job).ae2lt$getFastPlanningStats().recordFailure();
                process.ae2lt$setPossible(true);
                break;
            }

            long available = child.extract(this.what, totalRequestedItems - crafted, Actionable.MODULATE);
            if (available == 0) {
                process.ae2lt$setPossible(false);
                break;
            }

            child.applyDiff(inv);
            crafted += available;
        }
        return crafted;
    }

    @Unique
    private long ae2lt$requestBulkBranch(CraftingSimulationState inv,
                                         FastCraftingTreeProcess process,
                                         long totalRequestedItems)
            throws InterruptedException {
        ((FastCraftingCalculation) this.job).ae2lt$getFastPlanningStats().recordFastBranch();

        long craftedPerPattern = process.ae2lt$getOutputCount(this.what);
        if (craftedPerPattern <= 0) {
            process.ae2lt$setPossible(false);
            return 0;
        }

        long targetCopies = (totalRequestedItems + craftedPerPattern - 1) / craftedPerPattern;
        long copies = FastBranchCrafting.findMaxCraftableChecked(
                targetCopies,
                amount -> ae2lt$tryCraftBranch(inv, process, amount, totalRequestedItems, false) > 0,
                ((FastCraftingCalculation) this.job).ae2lt$getFastPlanningStats());

        if (copies <= 0) {
            long available = ae2lt$tryCraftBranch(inv, process, 1, totalRequestedItems, false);
            process.ae2lt$setPossible(available < 0);
            return 0;
        }

        long available = ae2lt$tryCraftBranch(inv, process, copies, totalRequestedItems, true);
        if (available <= 0) {
            process.ae2lt$setPossible(available < 0);
        }
        return Math.max(available, 0);
    }

    @Unique
    private long ae2lt$tryCraftBranch(CraftingSimulationState inv,
                                      FastCraftingTreeProcess process,
                                      long copies,
                                      long maxOutput,
                                      boolean apply)
            throws InterruptedException {
        if (copies <= 0 || maxOutput <= 0) {
            return 0;
        }

        IdentityHashMap<FastCraftingTreeProcess, Boolean> states = null;
        if (!apply) {
            states = new IdentityHashMap<>();
            process.ae2lt$captureFastProcessStates(states);
        }

        var child = new ChildCraftingSimulationState(inv);
        try {
            process.ae2lt$fastRequest(child, copies);

            long available = child.extract(this.what, maxOutput, Actionable.MODULATE);
            if (available == 0) {
                return 0;
            }

            if (apply) {
                child.applyDiff(inv);
            }
            return available;
        } catch (CraftBranchFailure ignored) {
            ((FastCraftingCalculation) this.job).ae2lt$getFastPlanningStats().recordFailure();
            return -1;
        } finally {
            if (states != null) {
                process.ae2lt$restoreFastProcessStates(states);
            }
        }
    }
}
