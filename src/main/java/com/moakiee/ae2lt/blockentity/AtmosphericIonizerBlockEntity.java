package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.item.WeatherCondensateItem;
import com.moakiee.ae2lt.logic.WeatherControlHelper;
import com.moakiee.ae2lt.machine.atmosphericionizer.AtmosphericIonizerInventory;
import com.moakiee.ae2lt.machine.atmosphericionizer.AtmosphericIonizerLogic;
import com.moakiee.ae2lt.machine.atmosphericionizer.AtmosphericIonizerStatus;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class AtmosphericIonizerBlockEntity extends AENetworkedBlockEntity implements IActionHost {
    public static final int PROCESS_TICKS = 5;
    private static final double POWER_EPSILON = 0.01D;

    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_LOCKED_TYPE = "LockedType";

    private final AtmosphericIonizerInventory inventory = new AtmosphericIonizerInventory(this::onInventoryChanged);
    private final AtmosphericIonizerLogic logic;

    private WeatherCondensateItem.Type lockedType;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean working;

    public AtmosphericIonizerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ATMOSPHERIC_IONIZER.get(), pos, blockState);
        this.logic = new AtmosphericIonizerLogic(this);
        getMainNode()
                .setIdlePowerUsage(0)
                .addService(appeng.api.networking.ticking.IGridTickable.class, logic);
    }

    public AtmosphericIonizerInventory getInventory() {
        return inventory;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return inventory;
    }

    public WeatherCondensateItem.Type getSelectedType() {
        return lockedType != null ? lockedType : WeatherCondensateItem.getType(getInstalledCondensate());
    }

    public boolean hasLockedType() {
        return lockedType != null;
    }

    public ItemStack getInstalledCondensate() {
        return inventory.getStackInSlot(AtmosphericIonizerInventory.SLOT_CONDENSATE);
    }

    public boolean hasLocalStartPrerequisites() {
        return getSelectedType() != null;
    }

    public boolean hasLockedCondensateInput() {
        return lockedType != null && lockedType == WeatherCondensateItem.getType(getInstalledCondensate());
    }

    public boolean canOperateInCurrentDimension() {
        return level instanceof ServerLevel serverLevel && WeatherControlHelper.supportsWeather(serverLevel);
    }

    public boolean isSelectedWeatherAlreadyActive() {
        WeatherCondensateItem.Type selectedType = getSelectedType();
        return selectedType != null && isWeatherAlreadyActive(selectedType);
    }

    public boolean isLockedWeatherAlreadyActive() {
        return lockedType != null && isWeatherAlreadyActive(lockedType);
    }

    public boolean hasEnoughEnergyForSelectedStart() {
        return canExtractAEPower(requiredEnergyForTick(getSelectedType(), 0, 0L));
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public long getTotalEnergy() {
        WeatherCondensateItem.Type type = getSelectedType();
        return type == null ? 0L : type.totalEnergy();
    }

    public long getRequiredEnergyForNextTick() {
        return requiredEnergyForTick(lockedType, processingTicksSpent, consumedEnergy);
    }

    public boolean canExtractAEPower(long amount) {
        return extractAEPower(amount, Actionable.SIMULATE);
    }

    public boolean tryExtractAEPower(long amount) {
        return extractAEPower(amount, Actionable.MODULATE);
    }

    public boolean isReadyToCommit() {
        return lockedType != null
                && processingTicksSpent >= PROCESS_TICKS
                && consumedEnergy >= lockedType.totalEnergy();
    }

    public boolean lockSelectedCondensate() {
        WeatherCondensateItem.Type selectedType = WeatherCondensateItem.getType(getInstalledCondensate());
        if (selectedType == null) {
            return false;
        }

        lockedType = selectedType;
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
        return true;
    }

    public void advanceProgress(long amount) {
        if (lockedType == null || amount <= 0L) {
            return;
        }

        consumedEnergy = Math.min(lockedType.totalEnergy(), consumedEnergy + amount);
        processingTicksSpent = Math.min(PROCESS_TICKS, processingTicksSpent + 1);
        saveChanges();
        markForClientUpdate();
    }

    public AtmosphericIonizerStatus getStatus() {
        WeatherCondensateItem.Type selectedType = getSelectedType();
        if (selectedType == null) {
            return AtmosphericIonizerStatus.IDLE;
        }

        if (!canOperateInCurrentDimension()) {
            return AtmosphericIonizerStatus.INVALID_DIMENSION;
        }

        if ((lockedType != null && isLockedWeatherAlreadyActive())
                || (lockedType == null && isSelectedWeatherAlreadyActive())) {
            return AtmosphericIonizerStatus.TARGET_ALREADY_ACTIVE;
        }

        if (lockedType == null) {
            return hasEnoughEnergyForSelectedStart()
                    ? AtmosphericIonizerStatus.READY
                    : AtmosphericIonizerStatus.WAITING_AE;
        }

        if (!hasLockedCondensateInput()) {
            return AtmosphericIonizerStatus.WAITING_INPUT;
        }

        if (isReadyToCommit()) {
            return AtmosphericIonizerStatus.READY;
        }

        return canExtractAEPower(getRequiredEnergyForNextTick())
                ? AtmosphericIonizerStatus.CHARGING
                : AtmosphericIonizerStatus.WAITING_AE;
    }

    public boolean commitLockedCondensate() {
        if (!(level instanceof ServerLevel serverLevel) || lockedType == null || !hasLockedCondensateInput()) {
            return false;
        }
        if (!WeatherControlHelper.supportsWeather(serverLevel) || lockedType.isActive(serverLevel)) {
            return false;
        }

        ItemStack extracted = inventory.extractItem(AtmosphericIonizerInventory.SLOT_CONDENSATE, 1, false);
        if (extracted.isEmpty()) {
            return false;
        }

        if (!lockedType.apply(serverLevel, serverLevel.random)) {
            inventory.insertItem(AtmosphericIonizerInventory.SLOT_CONDENSATE, extracted, false);
            return false;
        }

        lockedType = null;
        consumedEnergy = 0L;
        processingTicksSpent = 0;
        setWorking(false);
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(AtmosphericIonizerMenu.TYPE, player, locator);
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        if (this.working == working) {
            return;
        }
        this.working = working;
        markForClientUpdate();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        inventory.saveToTag(data, TAG_INVENTORY, registries);
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        if (lockedType != null) {
            data.putString(TAG_LOCKED_TYPE, lockedType.getSerializedName());
        } else {
            data.remove(TAG_LOCKED_TYPE);
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        inventory.loadFromTag(data, TAG_INVENTORY, registries);
        lockedType = data.contains(TAG_LOCKED_TYPE)
                ? WeatherCondensateItem.Type.fromName(data.getString(TAG_LOCKED_TYPE))
                : null;
        consumedEnergy = Math.max(0L, data.getLong(TAG_CONSUMED_ENERGY));
        processingTicksSpent = Math.max(0, data.getInt(TAG_PROCESSING_TICKS));

        if (lockedType == null) {
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            consumedEnergy = Math.min(consumedEnergy, lockedType.totalEnergy());
            processingTicksSpent = Math.min(processingTicksSpent, PROCESS_TICKS);
        }
        working = lockedType != null;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack condensate = getInstalledCondensate();
        if (!condensate.isEmpty()) {
            drops.add(condensate.copy());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        inventory.clear();
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.ATMOSPHERIC_IONIZER.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BACK));
    }

    private boolean isWeatherAlreadyActive(WeatherCondensateItem.Type type) {
        return level instanceof ServerLevel serverLevel && type.isActive(serverLevel);
    }

    private long requiredEnergyForTick(WeatherCondensateItem.Type type, int ticksSpent, long consumed) {
        if (type == null || ticksSpent >= PROCESS_TICKS) {
            return 0L;
        }

        long remainingEnergy = Math.max(0L, type.totalEnergy() - consumed);
        int remainingTicks = Math.max(1, PROCESS_TICKS - ticksSpent);
        return (remainingEnergy + remainingTicks - 1L) / remainingTicks;
    }

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private boolean extractAEPower(long amount, Actionable mode) {
        if (amount <= 0L) {
            return true;
        }

        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        double extracted = grid.getEnergyService().extractAEPower(amount, mode, PowerMultiplier.CONFIG);
        return extracted >= amount - POWER_EPSILON;
    }
}


