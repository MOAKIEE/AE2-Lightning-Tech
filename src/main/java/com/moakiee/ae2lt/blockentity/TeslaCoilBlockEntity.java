package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilAutomationInventory;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilEnergyStorage;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilInventory;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilLogic;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilStatus;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class TeslaCoilBlockEntity extends AENetworkedBlockEntity implements IActionHost {
    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int MAX_RECEIVE = 200_000;
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_SELECTED_MODE = "SelectedMode";
    private static final String TAG_LOCKED_MODE = "LockedMode";

    private final TeslaCoilInventory inventory = new TeslaCoilInventory(this::onInventoryChanged);
    private final TeslaCoilAutomationInventory automationInventory = new TeslaCoilAutomationInventory(inventory);
    private final TeslaCoilEnergyStorage energyStorage = new TeslaCoilEnergyStorage(
            ENERGY_CAPACITY,
            MAX_RECEIVE,
            this::onEnergyChanged);
    private final TeslaCoilLogic logic;

    private TeslaCoilMode selectedMode = TeslaCoilMode.HIGH_VOLTAGE;
    private TeslaCoilMode lockedMode;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean working;

    public TeslaCoilBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.TESLA_COIL.get(), pos, blockState);
        this.logic = new TeslaCoilLogic(this);
        getMainNode()
                .setIdlePowerUsage(0)
                .addService(IGridTickable.class, logic);
    }

    public TeslaCoilInventory getInventory() {
        return inventory;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return energyStorage;
    }

    public TeslaCoilEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public TeslaCoilLogic getLogic() {
        return logic;
    }

    public TeslaCoilMode getSelectedMode() {
        return selectedMode;
    }

    public TeslaCoilMode getCurrentMode() {
        return lockedMode != null ? lockedMode : selectedMode;
    }

    public void cycleMode() {
        if (lockedMode != null) {
            return;
        }

        selectedMode = selectedMode.next();
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    public boolean hasLockedMode() {
        return lockedMode != null;
    }

    public boolean lockSelectedMode() {
        if (lockedMode != null || !canStartSelectedMode()) {
            return false;
        }

        lockedMode = selectedMode;
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
        return true;
    }

    public boolean hasLocalStartPrerequisites() {
        return hasLocalPrerequisites(selectedMode);
    }

    public boolean hasLockedModeLocalPrerequisites() {
        return lockedMode != null && hasLocalPrerequisites(lockedMode);
    }

    public boolean canStartSelectedMode() {
        return hasLocalPrerequisites(selectedMode) && canCommitAgainstNetwork(selectedMode);
    }

    public boolean hasEnoughEnergyForSelectedStart() {
        return energyStorage.getStoredEnergyLong() >= selectedMode.requiredEnergyForTick(0, 0L);
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public int getProcessingTicksSpent() {
        return processingTicksSpent;
    }

    public long getRequiredEnergyForNextTick() {
        if (lockedMode == null) {
            return 0L;
        }
        return lockedMode.requiredEnergyForTick(processingTicksSpent, consumedEnergy);
    }

    public double getProgress() {
        if (lockedMode == null || lockedMode.totalEnergy() <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) lockedMode.totalEnergy());
    }

    public boolean isReadyToCommit() {
        return lockedMode != null
                && processingTicksSpent >= TeslaCoilMode.PROCESS_TICKS
                && consumedEnergy >= lockedMode.totalEnergy();
    }

    public void advanceProgress(long amount) {
        if (lockedMode == null || amount <= 0L) {
            return;
        }

        consumedEnergy = Math.min(lockedMode.totalEnergy(), consumedEnergy + amount);
        processingTicksSpent = Math.min(TeslaCoilMode.PROCESS_TICKS, processingTicksSpent + 1);
        saveChanges();
        markForClientUpdate();
    }

    public TeslaCoilStatus getStatus() {
        if (lockedMode == null) {
            return TeslaCoilStatus.IDLE;
        }

        if (isReadyToCommit()) {
            if (!hasLocalPrerequisites(lockedMode)) {
                return TeslaCoilStatus.WAITING_INPUTS;
            }
            return canCommitAgainstNetwork(lockedMode)
                    ? TeslaCoilStatus.READY
                    : TeslaCoilStatus.WAITING_NETWORK;
        }

        if (!hasLocalPrerequisites(lockedMode)) {
            return TeslaCoilStatus.WAITING_INPUTS;
        }

        long required = getRequiredEnergyForNextTick();
        if (required <= 0L) {
            return TeslaCoilStatus.READY;
        }

        return energyStorage.getStoredEnergyLong() >= required
                ? TeslaCoilStatus.CHARGING
                : TeslaCoilStatus.WAITING_FE;
    }

    public long getAvailableHighVoltage() {
        return getAvailableLightning(LightningKey.HIGH_VOLTAGE);
    }

    public long getAvailableExtremeHighVoltage() {
        return getAvailableLightning(LightningKey.EXTREME_HIGH_VOLTAGE);
    }

    public boolean isMatrixInstalled() {
        return inventory.hasMatrix();
    }

    public boolean commitLockedMode() {
        if (lockedMode == null || !canCommitAgainstNetwork(lockedMode) || !hasLocalPrerequisites(lockedMode)) {
            return false;
        }

        boolean committed = switch (lockedMode) {
            case HIGH_VOLTAGE -> commitHighVoltage();
            case EXTREME_HIGH_VOLTAGE -> commitExtremeHighVoltage();
        };
        if (!committed) {
            return false;
        }

        lockedMode = null;
        consumedEnergy = 0L;
        processingTicksSpent = 0;
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
        setWorking(false);
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(TeslaCoilMenu.TYPE, player, locator);
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        boolean changed = this.working != working;
        this.working = working;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(TeslaCoilBlock.WORKING)
                    && state.getValue(TeslaCoilBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(TeslaCoilBlock.WORKING, working), Block.UPDATE_ALL);
            } else if (changed) {
                markForClientUpdate();
            }
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        setWorking(hasLockedMode());
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        inventory.saveToTag(data, TAG_INVENTORY, registries);
        data.putLong(TAG_ENERGY, energyStorage.getStoredEnergyLong());
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        data.putString(TAG_SELECTED_MODE, selectedMode.getSerializedName());
        if (lockedMode != null) {
            data.putString(TAG_LOCKED_MODE, lockedMode.getSerializedName());
        } else {
            data.remove(TAG_LOCKED_MODE);
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        inventory.loadFromTag(data, TAG_INVENTORY, registries);
        energyStorage.loadStoredEnergy(data.getLong(TAG_ENERGY));
        selectedMode = TeslaCoilMode.fromName(data.getString(TAG_SELECTED_MODE));
        lockedMode = data.contains(TAG_LOCKED_MODE)
                ? TeslaCoilMode.fromName(data.getString(TAG_LOCKED_MODE))
                : null;
        consumedEnergy = Math.max(0L, data.getLong(TAG_CONSUMED_ENERGY));
        processingTicksSpent = Math.max(0, data.getInt(TAG_PROCESSING_TICKS));

        if (lockedMode == null) {
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            consumedEnergy = Math.min(consumedEnergy, lockedMode.totalEnergy());
            processingTicksSpent = Math.min(processingTicksSpent, TeslaCoilMode.PROCESS_TICKS);
        }
        working = lockedMode != null;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        inventory.clear();
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.TESLA_COIL.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    private boolean hasLocalPrerequisites(TeslaCoilMode mode) {
        return switch (mode) {
            case HIGH_VOLTAGE -> inventory.hasRequiredDust(mode.requiredDust());
            case EXTREME_HIGH_VOLTAGE -> inventory.hasMatrix();
        };
    }

    private boolean canCommitAgainstNetwork(TeslaCoilMode mode) {
        if (simulateInsert(mode.outputKey(), 1L) < 1L) {
            return false;
        }

        if (mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE) {
            return simulateExtract(LightningKey.HIGH_VOLTAGE, mode.requiredHighVoltage()) >= mode.requiredHighVoltage();
        }

        return true;
    }

    private long getAvailableLightning(LightningKey key) {
        return simulateExtract(key, Long.MAX_VALUE);
    }

    private boolean commitHighVoltage() {
        int requiredDust = lockedMode.requiredDust();
        ItemStack extractedDust = inventory.extractItem(TeslaCoilInventory.SLOT_DUST, requiredDust, false);
        if (extractedDust.getCount() != requiredDust) {
            return false;
        }

        long inserted = insert(lockedMode.outputKey(), 1L);
        if (inserted < 1L) {
            inventory.insertItem(TeslaCoilInventory.SLOT_DUST, extractedDust, false);
            return false;
        }

        return true;
    }

    private boolean commitExtremeHighVoltage() {
        long requiredHighVoltage = lockedMode.requiredHighVoltage();
        long extracted = extract(LightningKey.HIGH_VOLTAGE, requiredHighVoltage);
        if (extracted < requiredHighVoltage) {
            if (extracted > 0L) {
                insert(LightningKey.HIGH_VOLTAGE, extracted);
            }
            return false;
        }

        long inserted = insert(lockedMode.outputKey(), 1L);
        if (inserted < 1L) {
            insert(LightningKey.HIGH_VOLTAGE, extracted);
            return false;
        }

        return true;
    }

    private long simulateInsert(LightningKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory()
                .insert(key, amount, Actionable.SIMULATE, IActionSource.ofMachine(this));
    }

    private long insert(LightningKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory()
                .insert(key, amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    private long simulateExtract(LightningKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory()
                .extract(key, amount, Actionable.SIMULATE, IActionSource.ofMachine(this));
    }

    private long extract(LightningKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory()
                .extract(key, amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private void onEnergyChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }
}
