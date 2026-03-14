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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

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

    // --- Safe reflection lookups: return null on failure instead of crashing ---

    @Unique
    private static final @Nullable Class<?> AE2LT_ADV_LOGIC_CLASS =
            ae2lt$findClassSafe("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic");

    @Unique
    private static final @Nullable Class<?> AE2LT_ADV_JOB_CLASS =
            ae2lt$findClassSafe("net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob");

    @Unique
    private static final @Nullable Class<?> AE2LT_ADV_ELAPSED_TRACKER_CLASS =
            ae2lt$findClassSafe("net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker");

    @Unique
    private static final @Nullable Field AE2LT_ADV_JOB_FIELD =
            ae2lt$findDeclaredFieldSafe(AE2LT_ADV_LOGIC_CLASS, "job");

    @Unique
    private static final @Nullable Field AE2LT_ADV_INVENTORY_FIELD =
            ae2lt$findDeclaredFieldSafe(AE2LT_ADV_LOGIC_CLASS, "inventory");

    @Unique
    private static final @Nullable Field AE2LT_ADV_CPU_FIELD =
            ae2lt$findDeclaredFieldSafe(AE2LT_ADV_LOGIC_CLASS, "cpu");

    @Unique
    private static final @Nullable Method AE2LT_ADV_FINISH_JOB_METHOD =
            ae2lt$findDeclaredMethodSafe(AE2LT_ADV_LOGIC_CLASS, "finishJob", boolean.class);

    @Unique
    private static final @Nullable Method AE2LT_ADV_POST_CHANGE_METHOD =
            ae2lt$findDeclaredMethodSafe(AE2LT_ADV_LOGIC_CLASS, "postChange", AEKey.class);

    @Unique
    private static final @Nullable Field AE2LT_ADV_JOB_WAITING_FOR_FIELD =
            ae2lt$findDeclaredFieldSafe(AE2LT_ADV_JOB_CLASS, "waitingFor");

    @Unique
    private static final @Nullable Field AE2LT_ADV_JOB_TIME_TRACKER_FIELD =
            ae2lt$findDeclaredFieldSafe(AE2LT_ADV_JOB_CLASS, "timeTracker");

    @Unique
    private static final @Nullable Field AE2LT_ADV_JOB_FINAL_OUTPUT_FIELD =
            ae2lt$findDeclaredFieldSafe(AE2LT_ADV_JOB_CLASS, "finalOutput");

    @Unique
    private static final @Nullable Field AE2LT_ADV_JOB_REMAINING_AMOUNT_FIELD =
            ae2lt$findDeclaredFieldSafe(AE2LT_ADV_JOB_CLASS, "remainingAmount");

    @Unique
    private static final @Nullable Field AE2LT_ADV_JOB_LINK_FIELD =
            ae2lt$findDeclaredFieldSafe(AE2LT_ADV_JOB_CLASS, "link");

    @Unique
    private static final @Nullable Method AE2LT_ADV_DECREMENT_ITEMS_METHOD =
            ae2lt$findDeclaredMethodSafe(AE2LT_ADV_ELAPSED_TRACKER_CLASS, "decrementItems", long.class, AEKeyType.class);

    /**
     * Whether all required reflection targets are available.
     * If false, all injection handlers will gracefully skip (no-op).
     */
    @Unique
    private static final boolean AE2LT_ADV_AVAILABLE = ae2lt$checkAvailability();

    @Unique
    @Nullable
    private InsertContext ae2lt$insertContext;

    // ========================= Injection Handlers =========================

    @Inject(method = "insert", at = @At("HEAD"))
    private void ae2lt$beginInsertContext(AEKey what, long amount, Actionable type,
                                          CallbackInfoReturnable<Long> cir) {
        if (!AE2LT_ADV_AVAILABLE) return;
        this.ae2lt$insertContext = new InsertContext(what, amount, type);
    }

    @WrapOperation(
            method = "insert",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J",
                    ordinal = 0),
            remap = false)
    private long ae2lt$captureStrictWaitingMatch(ListCraftingInventory waitingFor, AEKey what, long amount,
                                                 Actionable mode, Operation<Long> original) {
        long strictMatched = original.call(waitingFor, what, amount, mode);
        if (AE2LT_ADV_AVAILABLE && mode == Actionable.SIMULATE && this.ae2lt$insertContext != null) {
            this.ae2lt$insertContext.setStrictMatched(strictMatched);
        }
        return strictMatched;
    }

    @Inject(method = "insert", at = @At("RETURN"), cancellable = true)
    private void ae2lt$claimOverloadRemainder(AEKey what, long amount, Actionable type,
                                              CallbackInfoReturnable<Long> cir) {
        if (!AE2LT_ADV_AVAILABLE) return;

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

        AE2LightningTech.LOGGER.debug(
                "AAE overload insert: cpu={} item={} remainder={}/{} mode={}",
                System.identityHashCode(this), what, remainder, ctx.getRequestedAmount(), type);

        var claims = OverloadCpuStateManager.INSTANCE.claim(this, what, remainder, type);
        if (!claims.claimedAnything()) {
            return;
        }

        if (type == Actionable.MODULATE) {
            ae2lt$deductClaimedWaitingFor(claims);
            long supplementalReturn = ae2lt$applyInventoryClaims(what, claims) + ae2lt$applyRequesterClaims(what, claims);
            var cpu = ae2lt$getCpu();
            if (cpu != null) {
                ((AdvCraftingCpuAccessor) cpu).invokeMarkDirty();
            }
            cir.setReturnValue(cir.getReturnValue() + supplementalReturn);
        } else {
            cir.setReturnValue(cir.getReturnValue() + claims.claimedAmount());
        }
    }

    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),
            remap = false)
    private boolean ae2lt$registerOverloadExpectedOutputs(ICraftingProvider provider, IPatternDetails details,
                                                          KeyCounter[] inputHolder, Operation<Boolean> original) {
        if (!AE2LT_ADV_AVAILABLE) {
            return original.call(provider, details, inputHolder);
        }

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

        boolean pushed = original.call(provider, details, inputHolder);
        var job = ae2lt$getJob();
        if (pushed && details instanceof OverloadedProviderOnlyPatternDetails overloadDetails && job != null) {
            var finalOutput = ae2lt$getJobFinalOutput(job);
            var finalOutputKey = finalOutput != null ? finalOutput.what() : null;
            CraftingLink link = ae2lt$getJobLink(job);
            if (link != null) {
                UUID craftingId = link.getCraftingID();
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
                AE2LightningTech.LOGGER.debug(
                        "AAE overload pattern registered: cpu={} pattern={} finalOutput={}",
                        System.identityHashCode(this),
                        overloadDetails.overloadPatternIdentity(),
                        finalOutputKey);
            }
        }
        return pushed;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void ae2lt$writeOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!AE2LT_ADV_AVAILABLE) return;
        var overloadStateTag = OverloadCpuStateManager.INSTANCE.writeToTag(this, registries);
        if (overloadStateTag != null) {
            data.put("ae2ltOverloadState", overloadStateTag);
        } else {
            data.remove("ae2ltOverloadState");
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void ae2lt$readOverloadState(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!AE2LT_ADV_AVAILABLE) return;
        OverloadCpuStateManager.INSTANCE.clear(this);
        var job = ae2lt$getJob();
        if (job != null && data.contains("ae2ltOverloadState", CompoundTag.TAG_COMPOUND)) {
            CraftingLink link = ae2lt$getJobLink(job);
            if (link != null) {
                OverloadCpuStateManager.INSTANCE.readFromTag(
                        this,
                        link.getCraftingID(),
                        data.getCompound("ae2ltOverloadState"),
                        registries);
            }
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void ae2lt$clearOverloadState(boolean success, CallbackInfo ci) {
        if (!AE2LT_ADV_AVAILABLE) return;
        OverloadCpuStateManager.INSTANCE.clear(this);
    }

    // ========================= Claim Application =========================

    @Unique
    private long ae2lt$applyInventoryClaims(AEKey incoming, OverloadClaimResult claims) {
        long claimed = claims.claimedForInventory();
        var job = ae2lt$getJob();
        if (claimed <= 0 || job == null) {
            return 0;
        }

        ae2lt$decrementJobItems(job, claimed, incoming.getType());
        var inventory = ae2lt$getInventory();
        if (inventory != null) {
            inventory.insert(incoming, claimed, Actionable.MODULATE);
        }
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
        CraftingLink link = ae2lt$getJobLink(job);
        long inserted = link != null ? link.insert(incoming, claimed, Actionable.MODULATE) : 0;
        ae2lt$invokePostChange(incoming);

        long remaining = Math.max(0L, ae2lt$getJobRemainingAmount(job) - claimed);
        ae2lt$setJobRemainingAmount(job, remaining);

        var cpu = ae2lt$getCpu();
        if (remaining <= 0) {
            ae2lt$invokeFinishJob(true);
            if (cpu != null) {
                ((AdvCraftingCpuAccessor) cpu).invokeUpdateOutput(null);
            }
        } else {
            GenericStack finalOutput = ae2lt$getJobFinalOutput(job);
            if (cpu != null && finalOutput != null) {
                ((AdvCraftingCpuAccessor) cpu).invokeUpdateOutput(new GenericStack(finalOutput.what(), remaining));
            }
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
        if (waitingFor == null) return;

        for (var claim : claims.claims()) {
            long deducted = waitingFor.extract(claim.exactExpectedKey(), claim.claimedAmount(), Actionable.MODULATE);
            AE2LightningTech.LOGGER.debug(
                    "AAE overload waitingFor deducted: key={} claimed={} deducted={} toRequester={}",
                    claim.exactExpectedKey(), claim.claimedAmount(), deducted, claim.routesToRequester());
        }
    }

    // ========================= Reflection Accessors (null-safe) =========================

    @Unique
    @Nullable
    private Object ae2lt$getJob() {
        return ae2lt$getFieldValueSafe(AE2LT_ADV_JOB_FIELD, this);
    }

    @Unique
    @Nullable
    private ListCraftingInventory ae2lt$getInventory() {
        Object val = ae2lt$getFieldValueSafe(AE2LT_ADV_INVENTORY_FIELD, this);
        return val instanceof ListCraftingInventory inv ? inv : null;
    }

    @Unique
    @Nullable
    private Object ae2lt$getCpu() {
        return ae2lt$getFieldValueSafe(AE2LT_ADV_CPU_FIELD, this);
    }

    @Unique
    @Nullable
    private ListCraftingInventory ae2lt$getJobWaitingFor(Object job) {
        Object val = ae2lt$getFieldValueSafe(AE2LT_ADV_JOB_WAITING_FOR_FIELD, job);
        return val instanceof ListCraftingInventory inv ? inv : null;
    }

    @Unique
    @Nullable
    private GenericStack ae2lt$getJobFinalOutput(Object job) {
        Object val = ae2lt$getFieldValueSafe(AE2LT_ADV_JOB_FINAL_OUTPUT_FIELD, job);
        return val instanceof GenericStack gs ? gs : null;
    }

    @Unique
    private long ae2lt$getJobRemainingAmount(Object job) {
        Object val = ae2lt$getFieldValueSafe(AE2LT_ADV_JOB_REMAINING_AMOUNT_FIELD, job);
        return val instanceof Long l ? l : 0L;
    }

    @Unique
    private void ae2lt$setJobRemainingAmount(Object job, long remainingAmount) {
        if (AE2LT_ADV_JOB_REMAINING_AMOUNT_FIELD == null) return;
        try {
            AE2LT_ADV_JOB_REMAINING_AMOUNT_FIELD.setLong(job, remainingAmount);
        } catch (ReflectiveOperationException e) {
            AE2LightningTech.LOGGER.warn("[ae2lt] Failed to update AdvancedAE remainingAmount", e);
        }
    }

    @Unique
    @Nullable
    private CraftingLink ae2lt$getJobLink(Object job) {
        Object val = ae2lt$getFieldValueSafe(AE2LT_ADV_JOB_LINK_FIELD, job);
        return val instanceof CraftingLink cl ? cl : null;
    }

    @Unique
    private void ae2lt$decrementJobItems(Object job, long amount, AEKeyType keyType) {
        if (AE2LT_ADV_JOB_TIME_TRACKER_FIELD == null || AE2LT_ADV_DECREMENT_ITEMS_METHOD == null) return;
        var timeTracker = ae2lt$getFieldValueSafe(AE2LT_ADV_JOB_TIME_TRACKER_FIELD, job);
        if (timeTracker == null) return;
        try {
            AE2LT_ADV_DECREMENT_ITEMS_METHOD.invoke(timeTracker, amount, keyType);
        } catch (ReflectiveOperationException e) {
            AE2LightningTech.LOGGER.warn("[ae2lt] Failed to decrement AdvancedAE elapsed time tracker", e);
        }
    }

    @Unique
    private void ae2lt$invokeFinishJob(boolean success) {
        if (AE2LT_ADV_FINISH_JOB_METHOD == null) return;
        try {
            AE2LT_ADV_FINISH_JOB_METHOD.invoke(this, success);
        } catch (ReflectiveOperationException e) {
            AE2LightningTech.LOGGER.warn("[ae2lt] Failed to invoke AdvancedAE finishJob", e);
        }
    }

    @Unique
    private void ae2lt$invokePostChange(AEKey what) {
        if (AE2LT_ADV_POST_CHANGE_METHOD == null) return;
        try {
            AE2LT_ADV_POST_CHANGE_METHOD.invoke(this, what);
        } catch (ReflectiveOperationException e) {
            AE2LightningTech.LOGGER.warn("[ae2lt] Failed to invoke AdvancedAE postChange", e);
        }
    }

    // ========================= Safe Reflection Utilities =========================

    @Unique
    @Nullable
    private static Object ae2lt$getFieldValueSafe(@Nullable Field field, Object target) {
        if (field == null) return null;
        try {
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            AE2LightningTech.LOGGER.warn("[ae2lt] Failed to read field {}", field.getName(), e);
            return null;
        }
    }

    @Unique
    private static boolean ae2lt$checkAvailability() {
        // All of these are critical for overload CPU logic to work.
        // If any is null, the entire feature is disabled for AdvancedAE CPUs.
        boolean available = AE2LT_ADV_LOGIC_CLASS != null
                && AE2LT_ADV_JOB_CLASS != null
                && AE2LT_ADV_ELAPSED_TRACKER_CLASS != null
                && AE2LT_ADV_JOB_FIELD != null
                && AE2LT_ADV_INVENTORY_FIELD != null
                && AE2LT_ADV_CPU_FIELD != null
                && AE2LT_ADV_FINISH_JOB_METHOD != null
                && AE2LT_ADV_POST_CHANGE_METHOD != null
                && AE2LT_ADV_JOB_WAITING_FOR_FIELD != null
                && AE2LT_ADV_JOB_TIME_TRACKER_FIELD != null
                && AE2LT_ADV_JOB_FINAL_OUTPUT_FIELD != null
                && AE2LT_ADV_JOB_REMAINING_AMOUNT_FIELD != null
                && AE2LT_ADV_JOB_LINK_FIELD != null
                && AE2LT_ADV_DECREMENT_ITEMS_METHOD != null;

        if (!available) {
            AE2LightningTech.LOGGER.warn(
                    "[ae2lt] AdvancedAE reflection targets not fully available. "
                            + "Overload CPU features will be disabled for AdvancedAE CPUs. "
                            + "This is expected if AdvancedAE is not installed or has an incompatible version.");
        } else {
            AE2LightningTech.LOGGER.info(
                    "[ae2lt] AdvancedAE reflection targets resolved successfully. "
                            + "Overload CPU features enabled for AdvancedAE CPUs.");
        }

        return available;
    }

    @Unique
    @Nullable
    private static Class<?> ae2lt$findClassSafe(String name) {
        try {
            return Class.forName(name);
        } catch (Exception e) {
            AE2LightningTech.LOGGER.debug("[ae2lt] AdvancedAE class not found: {} ({})", name, e.getMessage());
            return null;
        }
    }

    @Unique
    @Nullable
    private static Field ae2lt$findDeclaredFieldSafe(@Nullable Class<?> owner, String name) {
        if (owner == null) return null;
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            AE2LightningTech.LOGGER.debug("[ae2lt] AdvancedAE field not found: {}.{} ({})",
                    owner.getSimpleName(), name, e.getMessage());
            return null;
        }
    }

    @Unique
    @Nullable
    private static Method ae2lt$findDeclaredMethodSafe(@Nullable Class<?> owner, String name,
                                                        Class<?>... parameterTypes) {
        if (owner == null) return null;
        try {
            var method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            AE2LightningTech.LOGGER.debug("[ae2lt] AdvancedAE method not found: {}.{} ({})",
                    owner.getSimpleName(), name, e.getMessage());
            return null;
        }
    }
}
