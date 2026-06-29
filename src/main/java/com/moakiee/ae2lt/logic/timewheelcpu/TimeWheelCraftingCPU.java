package com.moakiee.ae2lt.logic.timewheelcpu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.blockentity.TestTimeWheelCraftingCpuBlockEntity;

public final class TimeWheelCraftingCPU implements ICraftingCPU {
    private final TestTimeWheelCraftingCpuBlockEntity host;
    private final long storageBytes;
    private final int coProcessors;
    private final Ae2LtTimeWheelCraftingCpuLogic craftingLogic = new Ae2LtTimeWheelCraftingCpuLogic(this);

    private GenericStack finalOutput;

    public TimeWheelCraftingCPU(TestTimeWheelCraftingCpuBlockEntity host, long storageBytes, int coProcessors) {
        this.host = host;
        this.storageBytes = storageBytes;
        this.coProcessors = coProcessors;
    }

    public Ae2LtTimeWheelCraftingCpuLogic getCraftingLogic() {
        return craftingLogic;
    }

    public TestTimeWheelCraftingCpuBlockEntity getHost() {
        return host;
    }

    @Override
    public boolean isBusy() {
        return craftingLogic.hasJob();
    }

    @Nullable
    @Override
    public CraftingJobStatus getJobStatus() {
        var output = craftingLogic.getFinalJobOutput();
        if (output == null) {
            return null;
        }

        var elapsedTimeTracker = craftingLogic.getElapsedTimeTracker();
        var progress = Math.max(
                0,
                elapsedTimeTracker.getStartItemCount() - elapsedTimeTracker.getRemainingItemCount());
        return new CraftingJobStatus(
                output,
                elapsedTimeTracker.getStartItemCount(),
                progress,
                elapsedTimeTracker.getElapsedTime());
    }

    @Override
    public void cancelJob() {
        craftingLogic.cancel();
    }

    @Override
    public long getAvailableStorage() {
        return storageBytes;
    }

    @Override
    public int getCoProcessors() {
        return coProcessors;
    }

    @Nullable
    @Override
    public Component getName() {
        return Component.translatable("block.ae2lt.test_time_wheel_crafting_cpu");
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return CpuSelectionMode.ANY;
    }

    public boolean isActive() {
        return host.isCpuActive();
    }

    @Nullable
    public IGrid getGrid() {
        return host.getGrid();
    }

    public IActionSource getSrc() {
        return host.getActionSource();
    }

    public Level getLevel() {
        return host.getLevel();
    }

    public boolean canBeAutoSelectedFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    public boolean isPreferredFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> false;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    public ICraftingSubmitResult submitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
                                           @Nullable ICraftingRequester requester) {
        return craftingLogic.trySubmitJob(grid, plan, src, requester);
    }

    public void updateOutput(@Nullable GenericStack stack) {
        if (stack != null && stack.amount() <= 0) {
            stack = null;
        }
        this.finalOutput = stack;
    }

    @Nullable
    public GenericStack getDisplayedOutput() {
        return finalOutput;
    }

    public void markDirty() {
        host.markCpuDirty();
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        craftingLogic.writeToNBT(tag, registries);
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        craftingLogic.readFromNBT(tag, registries);
    }

    public void resolvePendingLoad() {
        craftingLogic.resolvePendingLoad();
    }
}
