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
import appeng.api.config.PowerMultiplier;
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
import com.moakiee.ae2lt.config.AE2LTCommonConfig;

public class TestBatchCraftingCoreLogic extends PatternProviderLogic implements IBatchCraftingProvider {
    private static final String TAG_CORE = "BatchCraftingCore";
    private static final int TEST_MAX_THREADS = 1000;
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
        mainNode.setFlags();
        this.actionSource = new MachineSource(mainNode::getNode);
        this.core = new CraftingCore(
                new CoreHost(),
                new CoreParams(TEST_DELAY_TICKS, AE2LTCommonConfig.craftingCoreAePerCopy()),
                new MolecularCopyAssembler(host::getLevel),
                AE2LightningTech.craftingCoreRegistry());
        this.dispatcher = new CraftingCorePatternDispatcher(
                this::canAcceptBatch,
                this::hasLoadedPattern,
                core::pushBatch);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return super.getAvailablePatterns().stream()
                .filter(IMolecularAssemblerSupportedPattern.class::isInstance)
                .toList();
    }

    @Override
    public int pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, int maxCraft) {
        return dispatcher.pushBatch(details, scaledInputs, maxCraft);
    }

    @Override
    public int getBatchCapacity(IPatternDetails details) {
        if (!canAcceptBatch() || !hasLoadedPattern(details)) {
            return 0;
        }
        return core.availableCapacity();
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
        public int maxThreads() {
            return TEST_MAX_THREADS;
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
        public double extractEnergy(double amount) {
            var grid = gridNode.getGrid();
            if (grid == null || amount <= 0.0D) {
                return 0.0D;
            }
            return grid.getEnergyService().extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG);
        }

        @Override
        public void injectEnergy(double amount) {
            var grid = gridNode.getGrid();
            if (grid != null && amount > 0.0D) {
                grid.getEnergyService().injectPower(amount, Actionable.MODULATE);
            }
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
