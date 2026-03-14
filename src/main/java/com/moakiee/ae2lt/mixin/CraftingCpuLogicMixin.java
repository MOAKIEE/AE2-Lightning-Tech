package com.moakiee.ae2lt.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.moakiee.ae2lt.overload.cpu.InsertContext;
import com.moakiee.ae2lt.overload.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin {
    @Shadow(remap = false)
    CraftingCPUCluster cluster;

    @Unique
    @Nullable
    private InsertContext ae2lt$insertContext;

    @Inject(method = "insert", at = @At("HEAD"))
    private void ae2lt$beginInsertContext(AEKey what, long amount, Actionable type,
                                          CallbackInfoReturnable<Long> cir) {
        this.ae2lt$insertContext = new InsertContext(what, amount, type);
    }

    @Redirect(
            method = "insert",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J",
                    ordinal = 0
            ),
            remap = false
    )
    private long ae2lt$captureStrictWaitingMatch(ListCraftingInventory waitingFor, AEKey what, long amount,
                                                 Actionable mode) {
        long strictMatched = waitingFor.extract(what, amount, mode);
        if (mode == Actionable.SIMULATE && this.ae2lt$insertContext != null) {
            this.ae2lt$insertContext.setStrictMatched(strictMatched);
        }
        return strictMatched;
    }

    @Inject(method = "insert", at = @At("RETURN"), cancellable = true)
    private void ae2lt$claimOverloadRemainder(AEKey what, long amount, Actionable type,
                                              CallbackInfoReturnable<Long> cir) {
        var ctx = this.ae2lt$insertContext;
        this.ae2lt$insertContext = null;
        if (ctx == null || ctx.getRequestedAmount() <= 0) {
            return;
        }

        long remainder = Math.max(0L, ctx.getRequestedAmount() - ctx.getStrictMatched());
        if (remainder <= 0) {
            return;
        }

        var logic = (CraftingCpuLogic) (Object) this;
        if (OverloadCpuStateManager.INSTANCE.get(logic).isEmpty()) {
            return;
        }

        var secondary = OverloadCpuStateManager.INSTANCE.claimSecondary(logic, what, remainder, type);
        long remainingAfterSecondary = remainder - secondary.claimedAmount();
        var primary = remainingAfterSecondary > 0
                ? OverloadCpuStateManager.INSTANCE.claimPrimary(logic, what, remainingAfterSecondary, type)
                : OverloadClaimResult.EMPTY;

        if (!secondary.claimedAnything() && !primary.claimedAnything()) {
            return;
        }

        long supplementalReturn = 0;
        if (type == Actionable.MODULATE) {
            supplementalReturn += ae2lt$applySecondaryClaims(what, secondary);
            supplementalReturn += ae2lt$applyPrimaryClaims(what, primary);
            cluster.markDirty();
        } else {
            supplementalReturn += secondary.claimedAmount();
            supplementalReturn += primary.claimedAmount();
        }

        cir.setReturnValue(cir.getReturnValue() + supplementalReturn);
    }

    @Redirect(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"
            ),
            remap = false
    )
    private boolean ae2lt$registerOverloadExpectedOutputs(ICraftingProvider provider, IPatternDetails details,
                                                          KeyCounter[] inputHolder) {
        boolean pushed = provider.pushPattern(details, inputHolder);
        if (pushed && details instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
            OverloadCpuStateManager.INSTANCE.registerExpectedOutputs(
                    (CraftingCpuLogic) (Object) this,
                    new OverloadPatternReference(
                            overloadDetails.overloadPatternIdentity(),
                            overloadDetails.overloadPatternDetailsView().sourcePattern()),
                    overloadDetails.overloadPatternDetailsView(),
                    1L);
        }
        return pushed;
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void ae2lt$clearOverloadState(boolean success, CallbackInfo ci) {
        OverloadCpuStateManager.INSTANCE.clear((CraftingCpuLogic) (Object) this);
    }

    @Unique
    private long ae2lt$applySecondaryClaims(AEKey incoming, OverloadClaimResult claims) {
        if (!claims.claimedAnything()) {
            return 0;
        }

        var logic = (CraftingCpuLogic) (Object) this;
        var job = ((CraftingCpuLogicAccessor) logic).getJob();
        if (job == null) {
            return 0;
        }

        var jobAccessor = (ExecutingCraftingJobAccessor) job;
        ((ElapsedTimeTrackerAccessor) jobAccessor.getTimeTracker()).invokeDecrementItems(
                claims.claimedAmount(),
                incoming.getType());
        logic.getInventory().insert(incoming, claims.claimedAmount(), Actionable.MODULATE);
        return claims.claimedAmount();
    }

    @Unique
    private long ae2lt$applyPrimaryClaims(AEKey incoming, OverloadClaimResult claims) {
        if (!claims.claimedAnything()) {
            return 0;
        }

        var logic = (CraftingCpuLogic) (Object) this;
        var logicAccessor = (CraftingCpuLogicAccessor) logic;
        ExecutingCraftingJob job = logicAccessor.getJob();
        if (job == null) {
            return 0;
        }

        var jobAccessor = (ExecutingCraftingJobAccessor) job;
        ((ElapsedTimeTrackerAccessor) jobAccessor.getTimeTracker()).invokeDecrementItems(
                claims.claimedAmount(),
                incoming.getType());
        long inserted = jobAccessor.getLink().insert(incoming, claims.claimedAmount(), Actionable.MODULATE);
        logicAccessor.invokePostChange(incoming);

        long remaining = Math.max(0L, jobAccessor.getRemainingAmount() - claims.claimedAmount());
        jobAccessor.setRemainingAmount(remaining);

        if (remaining <= 0) {
            logicAccessor.invokeFinishJob(true);
            cluster.updateOutput(null);
        } else {
            GenericStack finalOutput = jobAccessor.getFinalOutput();
            cluster.updateOutput(new GenericStack(finalOutput.what(), remaining));
        }

        return inserted;
    }

}
