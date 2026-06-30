package com.moakiee.ae2lt.mixin;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.network.clientbound.CraftingStatusPacket;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;

import com.moakiee.thunderbolt.ae2.timewheel.Ae2LtTimeWheelCraftingCpuLogic;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCPU;

@Mixin(value = CraftingCPUMenu.class, remap = false)
public abstract class TimeWheelCraftingCPUMenuMixin extends AEBaseMenu {
    @Final
    @Shadow
    private IncrementalUpdateHelper incrementalUpdateHelper;

    @Final
    @Shadow
    private Consumer<AEKey> cpuChangeListener;

    @Shadow
    private boolean cachedSuspend;

    @Shadow
    private CraftingCPUCluster cpu;

    @Shadow
    public CpuSelectionMode schedulingMode;

    @Shadow
    public boolean cantStoreItems;

    @Unique
    private TimeWheelCraftingCPU ae2lt$timeWheelCpu;

    protected TimeWheelCraftingCPUMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "setCPU(Lappeng/api/networking/crafting/ICraftingCPU;)V", at = @At("HEAD"), cancellable = true)
    private void ae2lt$setTimeWheelCpu(ICraftingCPU selected, CallbackInfo ci) {
        if (this.ae2lt$timeWheelCpu != null) {
            this.ae2lt$timeWheelCpu.getCraftingLogic().removeListener(cpuChangeListener);
            this.ae2lt$timeWheelCpu = null;
        }

        if (!(selected instanceof TimeWheelCraftingCPU timeWheelCpu)) {
            return;
        }

        if (this.cpu != null) {
            this.cpu.craftingLogic.removeListener(cpuChangeListener);
            this.cpu = null;
        }

        this.incrementalUpdateHelper.reset();
        this.cachedSuspend = false;
        this.ae2lt$timeWheelCpu = timeWheelCpu;

        var allItems = new KeyCounter();
        timeWheelCpu.getCraftingLogic().getAllItems(allItems);
        for (var entry : allItems) {
            this.incrementalUpdateHelper.addChange(entry.getKey());
        }
        timeWheelCpu.getCraftingLogic().addListener(cpuChangeListener);

        ci.cancel();
    }

    @Inject(method = "cancelCrafting", at = @At("TAIL"))
    private void ae2lt$cancelTimeWheelCrafting(CallbackInfo ci) {
        if (!isClientSide() && this.ae2lt$timeWheelCpu != null) {
            this.ae2lt$timeWheelCpu.cancelJob();
        }
    }

    @Inject(method = "toggleScheduling", at = @At("TAIL"))
    private void ae2lt$toggleTimeWheelScheduling(CallbackInfo ci) {
        if (!isClientSide() && this.ae2lt$timeWheelCpu != null) {
            var logic = this.ae2lt$timeWheelCpu.getCraftingLogic();
            logic.setJobSuspended(!logic.isJobSuspended());
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void ae2lt$removed(Player player, CallbackInfo ci) {
        if (this.ae2lt$timeWheelCpu != null) {
            this.ae2lt$timeWheelCpu.getCraftingLogic().removeListener(cpuChangeListener);
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void ae2lt$broadcastTimeWheelStatus(CallbackInfo ci) {
        if (!isServerSide() || this.ae2lt$timeWheelCpu == null) {
            return;
        }

        var logic = this.ae2lt$timeWheelCpu.getCraftingLogic();
        this.schedulingMode = this.ae2lt$timeWheelCpu.getSelectionMode();
        this.cantStoreItems = logic.isCantStoreItems();

        if (this.incrementalUpdateHelper.hasChanges() || this.cachedSuspend != logic.isJobSuspended()) {
            var status = ae2lt$createStatus(this.incrementalUpdateHelper, logic);
            this.incrementalUpdateHelper.commitChanges();
            this.cachedSuspend = status.isSuspended();
            sendPacketToClient(new CraftingStatusPacket(containerId, status));
        }
    }

    @Unique
    private static CraftingStatus ae2lt$createStatus(IncrementalUpdateHelper changes,
                                                     Ae2LtTimeWheelCraftingCpuLogic logic) {
        boolean full = changes.isFullUpdate();
        var entries = new ArrayList<CraftingStatusEntry>();

        for (var what : changes) {
            long storedCount = logic.getStored(what);
            long activeCount = logic.getWaitingFor(what);
            long pendingCount = logic.getPendingOutputs(what);

            var sentStack = what;
            if (!full && changes.getSerial(what) != null) {
                sentStack = null;
            }

            var entry = new CraftingStatusEntry(
                    changes.getOrAssignSerial(what),
                    sentStack,
                    storedCount,
                    activeCount,
                    pendingCount);
            entries.add(entry);

            if (entry.isDeleted()) {
                changes.removeSerial(what);
            }
        }

        var tracker = logic.getElapsedTimeTracker();
        return new CraftingStatus(
                full,
                tracker.getElapsedTime(),
                tracker.getRemainingItemCount(),
                tracker.getStartItemCount(),
                entries,
                logic.isJobSuspended());
    }
}
