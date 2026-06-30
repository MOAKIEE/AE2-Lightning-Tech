package com.moakiee.ae2lt.mixin;

import java.util.IdentityHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.NetworkCraftingSimulationState;

import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingCalculation;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingTreeNode;
import com.moakiee.ae2lt.logic.timewheelcpu.FastCraftingTreeProcess;
import com.moakiee.ae2lt.logic.timewheelcpu.FastPlanningDecision;
import com.moakiee.ae2lt.logic.timewheelcpu.FastPlanningStats;
import com.moakiee.ae2lt.logic.timewheelcpu.FastPlanningWatchdog;
import com.qianchang.ae2lt_core.crafting.ae2.FastCraftingPlanner;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationFastMixin implements FastCraftingCalculation {
    @Unique
    private static final Logger AE2LT_FAST_LOG = LoggerFactory.getLogger("ae2lt-fast-crafting");

    @Unique
    private boolean ae2lt$fastPlanningEnabled;

    @Unique
    private final FastPlanningStats ae2lt$fastPlanningStats = new FastPlanningStats();

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private NetworkCraftingSimulationState networkInv;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private AEKey output;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    ICraftingSimulationRequester simRequester;

    @Shadow
    private boolean simulate;

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

    /**
     * Primary fast path: the Thunderbolt linear planner ({@link FastCraftingPlanner}).
     *
     * <p>Replaces AE2's exhaustive per-amount tree simulation with a linear topological backbone +
     * bounded backtracking that natively models byproducts, hard/cyclic fuzzy, containers, durability
     * tools and emittable items. It is best-effort and <b>never</b> falls back to AE2's exhaustive
     * simulator (Policy A) — that exponential/quadratic path is exactly what hung on
     * {@code mysterious_cell (with patches)}.
     *
     * <p>Only active when a TimeWheel CPU is live ({@link #ae2lt$fastPlanningEnabled}), matching the old
     * gating. When the planner declines (defensive, rare) or throws, this injector does nothing and the
     * original {@code runCraftAttempt} body runs — where the legacy {@link #ae2lt$requestWithFastPlanning}
     * redirect below still provides the previous fast/legacy behavior.
     */
    @Inject(method = "runCraftAttempt", at = @At("HEAD"), cancellable = true)
    private void ae2lt$thunderboltAttempt(boolean simulate, long amount,
                                          CallbackInfoReturnable<CraftingPlan> cir) {
        if (!this.ae2lt$fastPlanningEnabled) {
            return;
        }
        var gridNode = this.simRequester.getGridNode();
        if (gridNode == null) {
            return;
        }
        var craftingService = gridNode.getGrid().getCraftingService();

        this.ae2lt$fastPlanningStats.reset(System.nanoTime());
        FastPlanningWatchdog.start(
                "output=" + this.output + " requested=" + amount + " simulate=" + simulate + " engine=thunderbolt",
                this.ae2lt$fastPlanningStats);
        boolean handled = false;
        try {
            var attempt = FastCraftingPlanner.tryAttempt(craftingService, this.networkInv, this.output, amount, simulate);
            if (attempt.handled()) {
                handled = true;
                // Mirror the real body's side effect so isSimulation() reflects the produced plan.
                this.simulate = simulate;
                cir.setReturnValue(attempt.plan());
            }
        } catch (Throwable t) {
            AE2LT_FAST_LOG.warn(
                    "[ae2lt] THUNDERBOLT fast path threw, falling back to AE2 attempt: output={} amount={} simulate={}",
                    this.output, amount, simulate, t);
        } finally {
            this.ae2lt$fastPlanningStats.finish(System.nanoTime());
            FastPlanningWatchdog.stop();
            if (AE2LT_FAST_LOG.isInfoEnabled()) {
                AE2LT_FAST_LOG.info(
                        "[ae2lt] THUNDERBOLT crafting planning output={} amount={} simulate={} handled={} {}",
                        this.output, amount, simulate, handled, this.ae2lt$fastPlanningStats.summary(amount, !handled));
            }
        }
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
        boolean useFast = FastPlanningDecision.useFastAttempt(ae2lt$fastPlanningEnabled, this.isSimulation());
        boolean fallback = false;

        this.ae2lt$fastPlanningStats.reset(System.nanoTime());
        // Register this attempt with the watchdog for the whole duration (fast path, fallback, or the
        // legacy/simulation path) so a hang on *any* of them is captured with a live stack + counters.
        FastPlanningWatchdog.start(
                fastTree.ae2lt$describe(amount) + " fastPath=" + useFast + " simulation=" + this.isSimulation(),
                this.ae2lt$fastPlanningStats);
        try {
            if (!useFast) {
                fastTree.ae2lt$legacyRequest(inv, amount, containerItems);
                return;
            }

            var processStates = new IdentityHashMap<FastCraftingTreeProcess, Boolean>();
            fastTree.ae2lt$captureFastProcessStates(processStates);
            try {
                var child = new ChildCraftingSimulationState(inv);
                fastTree.ae2lt$fastRequest(child, amount, containerItems);
                child.applyDiff(inv);
            } catch (CraftBranchFailure fastFailure) {
                fallback = true;
                this.ae2lt$fastPlanningStats.recordFallback();
                fastTree.ae2lt$restoreFastProcessStates(processStates);
                fastTree.ae2lt$legacyRequest(inv, amount, containerItems);
            }
        } finally {
            this.ae2lt$fastPlanningStats.finish(System.nanoTime());
            FastPlanningWatchdog.stop();
            if (useFast && AE2LT_FAST_LOG.isInfoEnabled()) {
                AE2LT_FAST_LOG.info(
                        "[ae2lt] FAST crafting planning {}",
                        this.ae2lt$fastPlanningStats.summary(amount, fallback));
            }
        }
    }
}
