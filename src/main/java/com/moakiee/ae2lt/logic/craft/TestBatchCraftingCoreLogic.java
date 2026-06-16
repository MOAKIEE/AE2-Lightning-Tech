package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.helpers.MachineSource;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.api.crafting.IBatchCraftingProvider;
import com.moakiee.ae2lt.blockentity.TestBatchCraftingCoreBlockEntity;

/**
 * Single-block test rig that acts as the rate limiter in front of a {@link CraftingCore}:
 * it implements the {@link IBatchCraftingProvider} contract, caps copies by the engine's free
 * thread budget and schedules them with a fixed delay. The core itself only assembles + delivers.
 */
public class TestBatchCraftingCoreLogic extends PatternProviderLogic implements IBatchCraftingProvider {
    private static final String TAG_CORE = "BatchCraftingCore";
    private static final int TEST_MAX_THREADS = 2_000_000;
    private static final int TEST_DELAY_TICKS = 1;

    private final IManagedGridNode gridNode;
    private final TestBatchCraftingCoreBlockEntity host;
    private final IActionSource actionSource;
    private final CraftingCore core;
    private final CraftingCorePatternDispatcher dispatcher;

    public TestBatchCraftingCoreLogic(IManagedGridNode mainNode, TestBatchCraftingCoreBlockEntity host) {
        super(mainNode, host);
        this.gridNode = mainNode;
        this.host = host;
        this.actionSource = new MachineSource(mainNode::getNode);
        this.core = new CraftingCore(
                new CoreHost(),
                new MolecularCopyAssembler(host::getLevel),
                AE2LightningTech.craftingCoreRegistry());
        this.dispatcher = new CraftingCorePatternDispatcher(
                this::canAcceptBatch,
                this::hasLoadedPattern,
                this::limitedPush);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return super.getAvailablePatterns().stream()
                .filter(IMolecularAssemblerSupportedPattern.class::isInstance)
                .toList();
    }

    @Override
    public int pushBatch(IPatternDetails details, KeyCounter[] oneCopyTemplate, int maxCraft) {
        return dispatcher.pushBatch(details, oneCopyTemplate, maxCraft);
    }

    @Override
    public int getBatchCapacity(IPatternDetails details) {
        if (!canAcceptBatch() || !hasLoadedPattern(details)) {
            return 0;
        }
        return Math.max(0, TEST_MAX_THREADS - core.liveThreads());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return pushBatch(patternDetails, inputHolder, 1) == 0;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        var coreTag = new CompoundTag();
        core.writeTo(coreTag, registries);
        if (!coreTag.isEmpty()) {
            tag.put(TAG_CORE, coreTag);
        }
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        if (tag.contains(TAG_CORE, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            core.readFrom(tag.getCompound(TAG_CORE), registries);
        }
    }

    public int threadsInFlight() {
        return core.threadsInFlight();
    }

    /** Rate limiter: cap copies by the engine's free thread budget, then schedule with a fixed delay. */
    private int limitedPush(IPatternDetails details, KeyCounter[] oneCopyTemplate, int maxCraft) {
        if (maxCraft <= 0) return 0;
        int copies = Math.min(maxCraft, Math.max(0, TEST_MAX_THREADS - core.liveThreads()));
        if (copies <= 0) return maxCraft;
        core.pushBatch(details, oneCopyTemplate, copies, TEST_DELAY_TICKS);
        return maxCraft - copies;
    }

    private boolean canAcceptBatch() {
        return gridNode.isActive() && getCraftingLockedReason() == LockCraftingMode.NONE;
    }

    private boolean hasLoadedPattern(IPatternDetails details) {
        return super.getAvailablePatterns().contains(details);
    }

    private final class CoreHost implements CraftingCoreHost {
        @Override
        public long getGameTime() {
            Level level = host.getLevel();
            return level != null ? level.getGameTime() : 0L;
        }

        @Override
        public boolean isRemoved() {
            return host.isRemoved();
        }

        @Override
        public boolean isConnected() {
            return gridNode.isActive() && gridNode.getGrid() != null;
        }

        @Override
        public long insertToNetwork(AEKey key, long amount) {
            var grid = gridNode.getGrid();
            if (grid == null || amount <= 0) {
                return 0;
            }
            return grid.getStorageService().getInventory().insert(key, amount, Actionable.MODULATE, actionSource);
        }

        @Override
        public void spawnToWorld(AEKey key, long amount) {
            Level level = host.getLevel();
            if (level == null || level.isClientSide || key == null || amount <= 0) {
                return;
            }

            var drops = new ArrayList<ItemStack>();
            BlockPos pos = host.getBlockPos();
            key.addDrops(amount, drops, level, pos);
            for (var drop : drops) {
                if (!drop.isEmpty()) {
                    Block.popResource(level, pos, drop);
                }
            }
        }
    }
}
