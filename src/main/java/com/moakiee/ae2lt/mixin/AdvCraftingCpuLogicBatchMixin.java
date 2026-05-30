package com.moakiee.ae2lt.mixin;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;

import com.moakiee.ae2lt.logic.batch.AaeBatchJobView;
import com.moakiee.ae2lt.logic.batch.BatchExecutor;
import com.moakiee.ae2lt.logic.batch.BatchProviderFilterIterable;

@Pseudo
@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public abstract class AdvCraftingCpuLogicBatchMixin {
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    @Final
    AdvCraftingCPU cpu;

    @Unique
    private final Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> ae2lt$batchedByTask =
            new HashMap<>();

    @WrapOperation(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;executeCrafting"
                            + "(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
                            + "Lnet/minecraft/world/level/Level;)I"
            )
    )
    private int ae2lt$wrapExecuteCrafting(AdvCraftingCPULogic self,
                                          int remainingOps,
                                          CraftingService craftingService,
                                          IEnergyService energyService,
                                          Level level,
                                          Operation<Integer> original) {
        ae2lt$batchedByTask.clear();
        if (job == null) {
            return original.call(self, remainingOps, craftingService, energyService, level);
        }

        int batchPushed = BatchExecutor.runBatchOnly(
                remainingOps,
                craftingService,
                energyService,
                level,
                new AaeBatchJobView(job),
                inventory,
                ae2lt$batchedByTask,
                cpu::markDirty);

        if (batchPushed > 0) {
            // Batch providers account for their own parallel capacity; don't feed their copies into AE2's
            // rolling usedOps window, otherwise a 1000-thread core is throttled like ordinary co-processors.
            return 0;
        }

        return original.call(self, remainingOps, craftingService, energyService, level);
    }

    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/service/CraftingService;getProviders"
                            + "(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"
            )
    )
    private Iterable<ICraftingProvider> ae2lt$filterBatched(CraftingService craftingService,
                                                            IPatternDetails details,
                                                            Operation<Iterable<ICraftingProvider>> original) {
        var raw = original.call(craftingService, details);
        if (ae2lt$batchedByTask.isEmpty()) return raw;
        var perTask = ae2lt$batchedByTask.get(details);
        if (perTask == null || perTask.isEmpty()) return raw;
        return new BatchProviderFilterIterable(raw, perTask);
    }
}
