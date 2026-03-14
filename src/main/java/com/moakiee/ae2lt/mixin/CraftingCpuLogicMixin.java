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

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.overload.cpu.InsertContext;
import com.moakiee.ae2lt.overload.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

@Mixin(targets = "appeng.crafting.execution.CraftingCpuLogic", remap = false)
public abstract class CraftingCpuLogicMixin {
    @Shadow(remap = false)
    CraftingCPUCluster cluster;

    @Unique
    @Nullable
    private InsertContext ae2lt$insertContext;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$logCpuMixinApplied(CraftingCPUCluster cluster, CallbackInfo ci) {
        AE2LightningTech.LOGGER.info(
                "[ae2lt] CraftingCpuLogicMixin applied to CPU instance. cpu={} cluster={}",
                System.identityHashCode(this),
                cluster);
    }

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

        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        var pendingBefore = OverloadCpuStateManager.INSTANCE.snapshotPending(logic);
        if (pendingBefore.isEmpty()) {
            return;
        }

        AE2LightningTech.LOGGER.info(
                "[ae2lt] overload CPU insert observed pending outputs. cpu={} incoming={} requested={} strictMatched={} remainder={} mode={} pending={}",
                System.identityHashCode(logic),
                what,
                ctx.getRequestedAmount(),
                ctx.getStrictMatched(),
                remainder,
                type,
                ae2lt$describePending(pendingBefore));

        var claims = OverloadCpuStateManager.INSTANCE.claim(logic, what, remainder, type);
        if (!claims.claimedAnything()) {
            AE2LightningTech.LOGGER.info(
                    "[ae2lt] overload CPU insert found no ID_ONLY claim. cpu={} incoming={} requested={} strictMatched={} remainder={} mode={} pending={}",
                    System.identityHashCode(logic),
                    what,
                    ctx.getRequestedAmount(),
                    ctx.getStrictMatched(),
                    remainder,
                    type,
                    ae2lt$describePending(pendingBefore));
            return;
        }

        var pendingAfterClaim = OverloadCpuStateManager.INSTANCE.snapshotPending(logic);
        AE2LightningTech.LOGGER.info(
                "[ae2lt] overload CPU insert claimed ID_ONLY outputs. cpu={} incoming={} remainder={} mode={} claims={} pendingAfter={}",
                System.identityHashCode(logic),
                what,
                remainder,
                type,
                ae2lt$describeClaims(claims),
                ae2lt$describePending(pendingAfterClaim));

        long supplementalReturn = 0;
        if (type == Actionable.MODULATE) {
            var job = ((CraftingCpuLogicAccessor) logic).getJob();
            if (job != null) {
                ae2lt$deductClaimedWaitingFor(job, claims);
            }
            supplementalReturn += ae2lt$applyInventoryClaims(what, claims);
            supplementalReturn += ae2lt$applyRequesterClaims(what, claims);
            cluster.markDirty();
        } else {
            supplementalReturn += claims.claimedAmount();
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
        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        AE2LightningTech.LOGGER.info(
                "[ae2lt] CraftingCpuLogic executeCrafting redirect entered. cpu={} detailsClass={} outputs={}",
                System.identityHashCode(logic),
                details.getClass().getName(),
                details.getOutputs());
        OverloadPatternReference patternReference = null;
        if (details instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
            patternReference = new OverloadPatternReference(
                    overloadDetails.overloadPatternIdentity(),
                    overloadDetails.overloadPatternDetailsView().sourcePattern());
            if (OverloadCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(
                    logic,
                    patternReference,
                    overloadDetails.overloadPatternDetailsView())) {
                AE2LightningTech.LOGGER.debug(
                        "Deferring overload pattern push because concurrent ID_ONLY outputs with the same item id "
                                + "would become ambiguous. pattern={}",
                        overloadDetails.overloadPatternIdentity());
                return false;
            }
        }

        boolean pushed = provider.pushPattern(details, inputHolder);
        if (pushed && details instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
            var job = ((CraftingCpuLogicAccessor) logic).getJob();
            var finalOutputKey = job != null
                    ? ((ExecutingCraftingJobAccessor) job).getFinalOutput().what()
                    : null;
            OverloadCpuStateManager.INSTANCE.registerExpectedOutputs(
                    logic,
                    patternReference != null
                            ? patternReference
                            : new OverloadPatternReference(
                                    overloadDetails.overloadPatternIdentity(),
                                    overloadDetails.overloadPatternDetailsView().sourcePattern()),
                    overloadDetails.overloadPatternDetailsView(),
                    details.getOutputs(),
                    finalOutputKey,
                    1L);
            AE2LightningTech.LOGGER.info(
                    "[ae2lt] registered overload expected outputs. cpu={} pattern={} finalOutput={} actualOutputs={} pending={}",
                    System.identityHashCode(logic),
                    overloadDetails.overloadPatternIdentity(),
                    finalOutputKey,
                    details.getOutputs(),
                    ae2lt$describePending(OverloadCpuStateManager.INSTANCE.snapshotPending(logic)));
        }
        return pushed;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void ae2lt$writeOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        var overloadStateTag = OverloadCpuStateManager.INSTANCE.writeToTag(logic, registries);
        if (overloadStateTag != null) {
            data.put("ae2ltOverloadState", overloadStateTag);
        } else {
            data.remove("ae2ltOverloadState");
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void ae2lt$readOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        OverloadCpuStateManager.INSTANCE.clear(logic);
        var job = ((CraftingCpuLogicAccessor) logic).getJob();
        if (job != null && data.contains("ae2ltOverloadState", CompoundTag.TAG_COMPOUND)) {
            OverloadCpuStateManager.INSTANCE.readFromTag(logic, data.getCompound("ae2ltOverloadState"), registries);
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void ae2lt$clearOverloadState(boolean success, CallbackInfo ci) {
        OverloadCpuStateManager.INSTANCE.clear((appeng.crafting.execution.CraftingCpuLogic) (Object) this);
    }

    @Unique
    private long ae2lt$applyInventoryClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForInventory();
        if (claimed <= 0) {
            return 0;
        }

        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        var job = ((CraftingCpuLogicAccessor) logic).getJob();
        if (job == null) {
            return 0;
        }

        var jobAccessor = (ExecutingCraftingJobAccessor) job;
        ((ElapsedTimeTrackerAccessor) jobAccessor.getTimeTracker()).invokeDecrementItems(
                claimed,
                incoming.getType());
        logic.getInventory().insert(incoming, claimed, Actionable.MODULATE);
        return claimed;
    }

    @Unique
    private long ae2lt$applyRequesterClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForRequester();
        if (claimed <= 0) {
            return 0;
        }

        var logic = (appeng.crafting.execution.CraftingCpuLogic) (Object) this;
        var logicAccessor = (CraftingCpuLogicAccessor) logic;
        ExecutingCraftingJob job = logicAccessor.getJob();
        if (job == null) {
            return 0;
        }

        var jobAccessor = (ExecutingCraftingJobAccessor) job;
        ((ElapsedTimeTrackerAccessor) jobAccessor.getTimeTracker()).invokeDecrementItems(
                claimed,
                incoming.getType());
        long inserted = jobAccessor.getLink().insert(incoming, claimed, Actionable.MODULATE);
        logicAccessor.invokePostChange(incoming);

        long remaining = Math.max(0L, jobAccessor.getRemainingAmount() - claimed);
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

    @Unique
    private void ae2lt$deductClaimedWaitingFor(ExecutingCraftingJob job, OverloadClaimResult claims) {
        var waitingFor = ((ExecutingCraftingJobAccessor) job).getWaitingFor();
        for (var claim : claims.claims()) {
            long before = waitingFor.extract(claim.exactExpectedKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            long deducted = waitingFor.extract(claim.exactExpectedKey(), claim.claimedAmount(), Actionable.MODULATE);
            long after = waitingFor.extract(claim.exactExpectedKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            AE2LightningTech.LOGGER.info(
                    "[ae2lt] deducted waitingFor for overload claim. expectedKey={} claimedAmount={} deducted={} waitingBefore={} waitingAfter={} routesToRequester={}",
                    claim.exactExpectedKey(),
                    claim.claimedAmount(),
                    deducted,
                    before,
                    after,
                    claim.routesToRequester());
        }
    }

    @Unique
    private static String ae2lt$describePending(
            java.util.List<com.moakiee.ae2lt.overload.cpu.PendingOverloadOutput> pending) {
        if (pending.isEmpty()) {
            return "[]";
        }

        var parts = new java.util.ArrayList<String>(pending.size());
        for (var entry : pending) {
            parts.add("{pattern=" + entry.key().patternIdentity()
                    + ",slot=" + entry.key().outputSlotIndex()
                    + ",item=" + entry.itemId()
                    + ",remaining=" + entry.remainingAmount()
                    + ",expectedKey=" + entry.exactExpectedKey()
                    + ",toRequester=" + entry.routesToRequester()
                    + "}");
        }
        return parts.toString();
    }

    @Unique
    private static String ae2lt$describeClaims(OverloadClaimResult claims) {
        if (!claims.claimedAnything()) {
            return "[]";
        }

        var parts = new java.util.ArrayList<String>(claims.claims().size());
        for (var claim : claims.claims()) {
            parts.add("{pattern=" + claim.key().patternIdentity()
                    + ",slot=" + claim.key().outputSlotIndex()
                    + ",amount=" + claim.claimedAmount()
                    + ",expectedKey=" + claim.exactExpectedKey()
                    + ",toRequester=" + claim.routesToRequester()
                    + "}");
        }
        return parts.toString();
    }

}
