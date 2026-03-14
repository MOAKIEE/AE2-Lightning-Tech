package com.moakiee.ae2lt.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
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
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.overload.cpu.InsertContext;
import com.moakiee.ae2lt.overload.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvCraftingCpuLogicMixin {
    @Unique
    private static final Field AE2LT_ADV_JOB_FIELD = ae2lt$findField("job");

    @Unique
    private static final Field AE2LT_ADV_INVENTORY_FIELD = ae2lt$findField("inventory");

    @Unique
    private static final Field AE2LT_ADV_CPU_FIELD = ae2lt$findField("cpu");

    @Unique
    private static final Method AE2LT_ADV_FINISH_JOB_METHOD = ae2lt$findMethod("finishJob", boolean.class);

    @Unique
    private static final Method AE2LT_ADV_POST_CHANGE_METHOD = ae2lt$findMethod("postChange", AEKey.class);

    @Unique
    private static final Class<?> AE2LT_ADV_JOB_CLASS =
            ae2lt$findClass("net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob");

    @Unique
    private static final Field AE2LT_ADV_JOB_WAITING_FOR_FIELD = ae2lt$findDeclaredField(AE2LT_ADV_JOB_CLASS, "waitingFor");

    @Unique
    private static final Field AE2LT_ADV_JOB_TIME_TRACKER_FIELD = ae2lt$findDeclaredField(AE2LT_ADV_JOB_CLASS, "timeTracker");

    @Unique
    private static final Field AE2LT_ADV_JOB_FINAL_OUTPUT_FIELD = ae2lt$findDeclaredField(AE2LT_ADV_JOB_CLASS, "finalOutput");

    @Unique
    private static final Field AE2LT_ADV_JOB_REMAINING_AMOUNT_FIELD =
            ae2lt$findDeclaredField(AE2LT_ADV_JOB_CLASS, "remainingAmount");

    @Unique
    private static final Field AE2LT_ADV_JOB_LINK_FIELD = ae2lt$findDeclaredField(AE2LT_ADV_JOB_CLASS, "link");

    @Unique
    private static final Class<?> AE2LT_ADV_ELAPSED_TRACKER_CLASS =
            ae2lt$findClass("net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker");

    @Unique
    private static final Method AE2LT_ADV_DECREMENT_ITEMS_METHOD =
            ae2lt$findDeclaredMethod(AE2LT_ADV_ELAPSED_TRACKER_CLASS, "decrementItems", long.class, AEKeyType.class);

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
                    ordinal = 0),
            remap = false)
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

        var pendingBefore = OverloadCpuStateManager.INSTANCE.snapshotPending(this);
        if (pendingBefore.isEmpty()) {
            return;
        }

        AE2LightningTech.LOGGER.info(
                "[ae2lt] advancedae overload CPU insert observed pending outputs. cpu={} incoming={} requested={} strictMatched={} remainder={} mode={} pending={}",
                System.identityHashCode(this),
                what,
                ctx.getRequestedAmount(),
                ctx.getStrictMatched(),
                remainder,
                type,
                ae2lt$describePending(pendingBefore));

        var claims = OverloadCpuStateManager.INSTANCE.claim(this, what, remainder, type);
        if (!claims.claimedAnything()) {
            return;
        }

        if (type == Actionable.MODULATE) {
            ae2lt$deductClaimedWaitingFor(claims);
            long supplementalReturn = ae2lt$applyInventoryClaims(what, claims) + ae2lt$applyRequesterClaims(what, claims);
            ((AdvCraftingCpuAccessor) ae2lt$getCpu()).invokeMarkDirty();
            cir.setReturnValue(cir.getReturnValue() + supplementalReturn);
        } else {
            cir.setReturnValue(cir.getReturnValue() + claims.claimedAmount());
        }
    }

    @Redirect(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),
            remap = false)
    private boolean ae2lt$registerOverloadExpectedOutputs(ICraftingProvider provider, IPatternDetails details,
                                                          KeyCounter[] inputHolder) {
        OverloadPatternReference patternReference = null;
        if (details instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
            patternReference = new OverloadPatternReference(
                    overloadDetails.overloadPatternIdentity(),
                    overloadDetails.overloadPatternDetailsView().sourcePattern());
            if (OverloadCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(
                    this,
                    patternReference,
                    overloadDetails.overloadPatternDetailsView())) {
                return false;
            }
        }

        boolean pushed = provider.pushPattern(details, inputHolder);
        var job = ae2lt$getJob();
        if (pushed && details instanceof OverloadedProviderOnlyPatternDetails overloadDetails && job != null) {
            var finalOutput = ae2lt$getJobFinalOutput(job);
            var finalOutputKey = finalOutput != null ? finalOutput.what() : null;
            UUID craftingId = ae2lt$getJobLink(job).getCraftingID();
            OverloadCpuStateManager.INSTANCE.registerExpectedOutputs(
                    this,
                    craftingId,
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
                    "[ae2lt] advancedae registered overload expected outputs. cpu={} pattern={} finalOutput={} actualOutputs={} pending={}",
                    System.identityHashCode(this),
                    overloadDetails.overloadPatternIdentity(),
                    finalOutputKey,
                    details.getOutputs(),
                    ae2lt$describePending(OverloadCpuStateManager.INSTANCE.snapshotPending(this)));
        }
        return pushed;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void ae2lt$writeOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        var overloadStateTag = OverloadCpuStateManager.INSTANCE.writeToTag(this, registries);
        if (overloadStateTag != null) {
            data.put("ae2ltOverloadState", overloadStateTag);
        } else {
            data.remove("ae2ltOverloadState");
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void ae2lt$readOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        OverloadCpuStateManager.INSTANCE.clear(this);
        var job = ae2lt$getJob();
        if (job != null && data.contains("ae2ltOverloadState", CompoundTag.TAG_COMPOUND)) {
            OverloadCpuStateManager.INSTANCE.readFromTag(
                    this,
                    ae2lt$getJobLink(job).getCraftingID(),
                    data.getCompound("ae2ltOverloadState"),
                    registries);
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void ae2lt$clearOverloadState(boolean success, CallbackInfo ci) {
        OverloadCpuStateManager.INSTANCE.clear(this);
    }

    @Unique
    private long ae2lt$applyInventoryClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForInventory();
        var job = ae2lt$getJob();
        if (claimed <= 0 || job == null) {
            return 0;
        }

        ae2lt$decrementJobItems(job, claimed, incoming.getType());
        ae2lt$getInventory().insert(incoming, claimed, Actionable.MODULATE);
        return claimed;
    }

    @Unique
    private long ae2lt$applyRequesterClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForRequester();
        var job = ae2lt$getJob();
        if (claimed <= 0 || job == null) {
            return 0;
        }

        ae2lt$decrementJobItems(job, claimed, incoming.getType());
        long inserted = ae2lt$getJobLink(job).insert(incoming, claimed, Actionable.MODULATE);
        ae2lt$invokePostChange(incoming);

        long remaining = Math.max(0L, ae2lt$getJobRemainingAmount(job) - claimed);
        ae2lt$setJobRemainingAmount(job, remaining);

        if (remaining <= 0) {
            ae2lt$invokeFinishJob(true);
            ((AdvCraftingCpuAccessor) ae2lt$getCpu()).invokeUpdateOutput(null);
        } else {
            GenericStack finalOutput = ae2lt$getJobFinalOutput(job);
            ((AdvCraftingCpuAccessor) ae2lt$getCpu()).invokeUpdateOutput(new GenericStack(finalOutput.what(), remaining));
        }

        return inserted;
    }

    @Unique
    private void ae2lt$deductClaimedWaitingFor(OverloadClaimResult claims) {
        var job = ae2lt$getJob();
        if (job == null) {
            return;
        }

        var waitingFor = ae2lt$getJobWaitingFor(job);
        for (var claim : claims.claims()) {
            long deducted = waitingFor.extract(claim.exactExpectedKey(), claim.claimedAmount(), Actionable.MODULATE);
            AE2LightningTech.LOGGER.info(
                    "[ae2lt] advancedae deducted waitingFor for overload claim. expectedKey={} claimedAmount={} deducted={} routesToRequester={}",
                    claim.exactExpectedKey(),
                    claim.claimedAmount(),
                    deducted,
                    claim.routesToRequester());
        }
    }

    @Unique
    @Nullable
    private Object ae2lt$getJob() {
        return ae2lt$getFieldValue(AE2LT_ADV_JOB_FIELD);
    }

    @Unique
    private ListCraftingInventory ae2lt$getInventory() {
        return (ListCraftingInventory) ae2lt$getFieldValue(AE2LT_ADV_INVENTORY_FIELD);
    }

    @Unique
    private Object ae2lt$getCpu() {
        return ae2lt$getFieldValue(AE2LT_ADV_CPU_FIELD);
    }

    @Unique
    private ListCraftingInventory ae2lt$getJobWaitingFor(Object job) {
        return (ListCraftingInventory) ae2lt$getFieldValue(AE2LT_ADV_JOB_WAITING_FOR_FIELD, job);
    }

    @Unique
    private GenericStack ae2lt$getJobFinalOutput(Object job) {
        return (GenericStack) ae2lt$getFieldValue(AE2LT_ADV_JOB_FINAL_OUTPUT_FIELD, job);
    }

    @Unique
    private long ae2lt$getJobRemainingAmount(Object job) {
        return ((Long) ae2lt$getFieldValue(AE2LT_ADV_JOB_REMAINING_AMOUNT_FIELD, job)).longValue();
    }

    @Unique
    private void ae2lt$setJobRemainingAmount(Object job, long remainingAmount) {
        try {
            AE2LT_ADV_JOB_REMAINING_AMOUNT_FIELD.setLong(job, remainingAmount);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to update AdvancedAE remainingAmount", e);
        }
    }

    @Unique
    private CraftingLink ae2lt$getJobLink(Object job) {
        return (CraftingLink) ae2lt$getFieldValue(AE2LT_ADV_JOB_LINK_FIELD, job);
    }

    @Unique
    private void ae2lt$decrementJobItems(Object job, long amount, AEKeyType keyType) {
        var timeTracker = ae2lt$getFieldValue(AE2LT_ADV_JOB_TIME_TRACKER_FIELD, job);
        try {
            AE2LT_ADV_DECREMENT_ITEMS_METHOD.invoke(timeTracker, amount, keyType);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to decrement AdvancedAE elapsed time tracker", e);
        }
    }

    @Unique
    private Object ae2lt$getFieldValue(Field field) {
        return ae2lt$getFieldValue(field, this);
    }

    @Unique
    private Object ae2lt$getFieldValue(Field field, Object target) {
        try {
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to read field " + field.getName(), e);
        }
    }

    @Unique
    private void ae2lt$invokeFinishJob(boolean success) {
        try {
            AE2LT_ADV_FINISH_JOB_METHOD.invoke(this, success);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke AdvancedAE finishJob", e);
        }
    }

    @Unique
    private void ae2lt$invokePostChange(AEKey what) {
        try {
            AE2LT_ADV_POST_CHANGE_METHOD.invoke(this, what);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke AdvancedAE postChange", e);
        }
    }

    @Unique
    private static Field ae2lt$findField(String name) {
        return ae2lt$findDeclaredField(
                ae2lt$findClass("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic"),
                name);
    }

    @Unique
    private static Method ae2lt$findMethod(String name, Class<?>... parameterTypes) {
        return ae2lt$findDeclaredMethod(
                ae2lt$findClass("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic"),
                name,
                parameterTypes);
    }

    @Unique
    private static Class<?> ae2lt$findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Unique
    private static Field ae2lt$findDeclaredField(Class<?> owner, String name) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Unique
    private static Method ae2lt$findDeclaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            var method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
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
}
