package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.block.OverloadProcessingFactoryBlock;
import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryAutomationInventory;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryEnergyStorage;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryFluidHandler;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryLogic;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingLockedRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCandidate;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class OverloadProcessingFactoryBlockEntity extends AENetworkedBlockEntity
        implements IUpgradeableObject, IActionHost {
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_UPGRADES = "Upgrades";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_INPUT_TANK = "InputTank";
    private static final String TAG_OUTPUT_TANK = "OutputTank";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_LOCKED_RECIPE = "LockedRecipe";

    public static final int ENERGY_CAPACITY = 64_000_000;
    public static final int ENERGY_RECEIVE_PER_OPERATION = 6_400_000;
    public static final int INPUT_TANK_CAPACITY = 512_000;
    public static final int OUTPUT_TANK_CAPACITY = 512_000;
    public static final int SPEED_CARD_SLOTS = 4;

    private final OverloadProcessingFactoryInventory inventory =
            new OverloadProcessingFactoryInventory(this::onInventoryChanged);
    private final OverloadProcessingFactoryAutomationInventory automationInventory =
            new OverloadProcessingFactoryAutomationInventory(inventory);
    private final NotifyingFluidTank inputTank =
            new NotifyingFluidTank(INPUT_TANK_CAPACITY, this::onTankChanged);
    private final NotifyingFluidTank outputTank =
            new NotifyingFluidTank(OUTPUT_TANK_CAPACITY, this::onTankChanged);
    private final OverloadProcessingFactoryFluidHandler fluidHandler =
            new OverloadProcessingFactoryFluidHandler(inputTank, outputTank);
    private final OverloadProcessingFactoryEnergyStorage energyStorage =
            new OverloadProcessingFactoryEnergyStorage(ENERGY_CAPACITY, ENERGY_RECEIVE_PER_OPERATION, this::onEnergyChanged);
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get(), SPEED_CARD_SLOTS, this::onUpgradesChanged);
    private final OverloadProcessingFactoryLogic logic;

    private OverloadProcessingLockedRecipe lockedRecipe;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean working;

    public OverloadProcessingFactoryBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(), pos, blockState);
        this.logic = new OverloadProcessingFactoryLogic(this);
        getMainNode()
                .setIdlePowerUsage(0)
                .addService(IGridTickable.class, logic);
    }

    public OverloadProcessingFactoryInventory getInventory() {
        return inventory;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public IFluidHandler getFluidHandlerCapability(Direction side) {
        return fluidHandler;
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return energyStorage;
    }

    public OverloadProcessingFactoryEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public FluidStack getInputFluid() {
        return inputTank.getFluid().copy();
    }

    public FluidStack getOutputFluid() {
        return outputTank.getFluid().copy();
    }

    public int getInstalledMatrixCount() {
        return inventory.getInstalledMatrixCount();
    }

    public Optional<OverloadProcessingRecipeCandidate> findProcessableRecipe() {
        return OverloadProcessingRecipeService.findFirstProcessable(
                getLevel(),
                inventory,
                getInputFluid(),
                getOutputFluid(),
                getAvailableHighVoltage(),
                getAvailableExtremeHighVoltage());
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public int getProcessingTicksSpent() {
        return processingTicksSpent;
    }

    public double getProgress() {
        if (lockedRecipe == null || lockedRecipe.totalEnergy() <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) lockedRecipe.totalEnergy());
    }

    public void addConsumedEnergy(long amount) {
        if (amount <= 0L) {
            return;
        }
        if (amount > Long.MAX_VALUE - this.consumedEnergy) {
            this.consumedEnergy = Long.MAX_VALUE;
        } else {
            this.consumedEnergy += amount;
        }
        saveChanges();
        markForClientUpdate();
    }

    public void incrementProcessingTicksSpent() {
        this.processingTicksSpent++;
        saveChanges();
    }

    public void resetProgressState() {
        boolean changed = this.consumedEnergy != 0L || this.processingTicksSpent != 0;
        this.consumedEnergy = 0L;
        this.processingTicksSpent = 0;
        if (changed) {
            saveChanges();
            markForClientUpdate();
        }
    }

    public boolean hasLockedRecipe() {
        return lockedRecipe != null;
    }

    public Optional<OverloadProcessingLockedRecipe> getLockedRecipe() {
        return Optional.ofNullable(lockedRecipe);
    }

    public Optional<OverloadProcessingLockedRecipe> lockCurrentRecipe() {
        if (lockedRecipe != null) {
            return Optional.of(lockedRecipe);
        }

        Optional<OverloadProcessingRecipeCandidate> candidate = findProcessableRecipe();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        lockedRecipe = OverloadProcessingLockedRecipe.fromCandidate(candidate.get());
        saveChanges();
        return Optional.of(lockedRecipe);
    }

    public void clearLockedRecipe() {
        if (lockedRecipe == null) {
            return;
        }
        lockedRecipe = null;
        saveChanges();
    }

    public void abortProcessing() {
        clearLockedRecipe();
        resetProgressState();
        setWorking(false);
    }

    public long getAvailableHighVoltage() {
        return simulateLightningExtract(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE);
    }

    public long getAvailableExtremeHighVoltage() {
        return simulateLightningExtract(LightningKey.EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE);
    }

    public boolean completeLockedRecipe(
            OverloadProcessingLockedRecipe lockedRecipe,
            OverloadProcessingRecipeCandidate candidate) {
        if (candidate.parallel() != lockedRecipe.parallel()) {
            return false;
        }
        if (!inventory.canAcceptRecipeOutputs(candidate.recipe().value().getScaledItemResults(candidate.parallel()))) {
            return false;
        }

        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            int toConsume = candidate.match().getConsumptionForSlot(slot);
            if (toConsume <= 0) {
                continue;
            }
            if (inventory.getStackInSlot(slot).getCount() < toConsume) {
                return false;
            }
        }

        FluidStack requiredInputFluid = candidate.recipe().value().fluidInput();
        int inputFluidCost = requiredInputFluid.isEmpty() ? 0 : requiredInputFluid.getAmount() * candidate.parallel();
        if (inputFluidCost > 0) {
            FluidStack currentInput = inputTank.getFluid();
            if (currentInput.isEmpty()
                    || !FluidStack.isSameFluidSameComponents(requiredInputFluid, currentInput)
                    || currentInput.getAmount() < inputFluidCost) {
                return false;
            }
        }

        FluidStack scaledOutputFluid = candidate.recipe().value().getScaledFluidResult(candidate.parallel());
        if (!canAcceptFluidOutput(scaledOutputFluid)) {
            return false;
        }

        var lightningPlan = OverloadProcessingRecipeService.resolveLightningConsumption(
                inventory,
                lockedRecipe.lightningTier(),
                lockedRecipe.totalLightningCost(),
                getAvailableHighVoltage(),
                getAvailableExtremeHighVoltage());
        if (lightningPlan.isEmpty()) {
            return false;
        }
        var plan = lightningPlan.get();
        if (simulateLightningExtract(plan.key(), plan.amount()) < plan.amount()) {
            return false;
        }

        ItemStack[] extractedInputs = new ItemStack[OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT];
        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            int toConsume = candidate.match().getConsumptionForSlot(slot);
            if (toConsume <= 0) {
                continue;
            }

            ItemStack extracted = inventory.extractItem(slot, toConsume, false);
            if (extracted.getCount() != toConsume) {
                rollbackInputs(extractedInputs);
                return false;
            }
            extractedInputs[slot] = extracted;
        }

        FluidStack drainedInput = inputFluidCost <= 0
                ? FluidStack.EMPTY
                : inputTank.drain(inputFluidCost, FluidAction.EXECUTE);
        if (inputFluidCost > 0 && drainedInput.getAmount() != inputFluidCost) {
            rollbackInputs(extractedInputs);
            if (!drainedInput.isEmpty()) {
                inputTank.fill(drainedInput, FluidAction.EXECUTE);
            }
            return false;
        }

        long extractedLightning = extractLightning(plan.key(), plan.amount());
        if (extractedLightning < plan.amount()) {
            rollbackInputs(extractedInputs);
            if (!drainedInput.isEmpty()) {
                inputTank.fill(drainedInput, FluidAction.EXECUTE);
            }
            if (extractedLightning > 0L) {
                insertLightning(plan.key(), extractedLightning);
            }
            return false;
        }

        if (!inventory.insertRecipeOutputs(candidate.recipe().value().getScaledItemResults(candidate.parallel()))) {
            insertLightning(plan.key(), extractedLightning);
            if (!drainedInput.isEmpty()) {
                inputTank.fill(drainedInput, FluidAction.EXECUTE);
            }
            rollbackInputs(extractedInputs);
            return false;
        }

        int filledFluid = scaledOutputFluid.isEmpty() ? 0 : outputTank.fill(scaledOutputFluid, FluidAction.EXECUTE);
        if (!scaledOutputFluid.isEmpty() && filledFluid != scaledOutputFluid.getAmount()) {
            insertLightning(plan.key(), extractedLightning);
            if (!drainedInput.isEmpty()) {
                inputTank.fill(drainedInput, FluidAction.EXECUTE);
            }
            rollbackItemOutputs(candidate.recipe().value().getScaledItemResults(candidate.parallel()));
            rollbackInputs(extractedInputs);
            return false;
        }

        clearLockedRecipe();
        resetProgressState();
        setWorking(false);
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(OverloadProcessingFactoryMenu.TYPE, player, locator);
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        boolean changed = this.working != working;
        this.working = working;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(OverloadProcessingFactoryBlock.WORKING)
                    && state.getValue(OverloadProcessingFactoryBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(OverloadProcessingFactoryBlock.WORKING, working), Block.UPDATE_ALL);
            } else if (changed) {
                markForClientUpdate();
            }
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        setWorking(hasLockedRecipe());
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        inventory.saveToTag(data, TAG_INVENTORY, registries);
        upgrades.writeToNBT(data, TAG_UPGRADES, registries);
        data.putLong(TAG_ENERGY, energyStorage.getStoredEnergyLong());
        data.put(TAG_INPUT_TANK, inputTank.writeToNBT(registries, new CompoundTag()));
        data.put(TAG_OUTPUT_TANK, outputTank.writeToNBT(registries, new CompoundTag()));
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        if (lockedRecipe != null) {
            data.put(TAG_LOCKED_RECIPE, lockedRecipe.toTag(registries));
        } else {
            data.remove(TAG_LOCKED_RECIPE);
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        inventory.loadFromTag(data, TAG_INVENTORY, registries);
        upgrades.readFromNBT(data, TAG_UPGRADES, registries);
        energyStorage.loadStoredEnergy(data.getLong(TAG_ENERGY));
        inputTank.readFromNBT(registries, data.getCompound(TAG_INPUT_TANK));
        outputTank.readFromNBT(registries, data.getCompound(TAG_OUTPUT_TANK));
        consumedEnergy = Math.max(0L, data.getLong(TAG_CONSUMED_ENERGY));
        processingTicksSpent = Math.max(0, data.getInt(TAG_PROCESSING_TICKS));
        if (data.contains(TAG_LOCKED_RECIPE, Tag.TAG_COMPOUND)) {
            lockedRecipe = OverloadProcessingLockedRecipe.fromTag(data.getCompound(TAG_LOCKED_RECIPE), registries);
        } else {
            lockedRecipe = null;
        }

        if (lockedRecipe == null) {
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            consumedEnergy = Math.min(consumedEnergy, lockedRecipe.totalEnergy());
            processingTicksSpent = Math.min(processingTicksSpent, OverloadProcessingFactoryLogic.MIN_PROCESS_TICKS);
        }
        working = lockedRecipe != null;
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
        for (var upgrade : upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        inventory.clear();
        upgrades.clear();
        inputTank.setFluid(FluidStack.EMPTY);
        outputTank.setFluid(FluidStack.EMPTY);
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.OVERLOAD_PROCESSING_FACTORY.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    private boolean canAcceptFluidOutput(FluidStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        FluidStack current = outputTank.getFluid();
        if (current.isEmpty()) {
            return stack.getAmount() <= OUTPUT_TANK_CAPACITY;
        }
        return FluidStack.isSameFluidSameComponents(current, stack)
                && current.getAmount() + stack.getAmount() <= OUTPUT_TANK_CAPACITY;
    }

    private void rollbackInputs(ItemStack[] extractedInputs) {
        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            ItemStack extracted = extractedInputs[slot];
            if (extracted != null && !extracted.isEmpty()) {
                inventory.insertItem(slot, extracted, false);
            }
        }
    }

    private void rollbackItemOutputs(List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            int remaining = output.getCount();
            for (int slot = OverloadProcessingFactoryInventory.SLOT_OUTPUT_0;
                 slot < OverloadProcessingFactoryInventory.SLOT_OUTPUT_0
                         + OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT && remaining > 0;
                 slot++) {
                ItemStack current = inventory.getStackInSlot(slot);
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, output)) {
                    continue;
                }

                int extracted = Math.min(remaining, current.getCount());
                inventory.extractItem(slot, extracted, false);
                remaining -= extracted;
            }
        }
    }

    private long simulateLightningExtract(LightningKey key, long amount) {
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

    private long extractLightning(LightningKey key, long amount) {
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

    private long insertLightning(LightningKey key, long amount) {
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

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private void onTankChanged() {
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private void onEnergyChanged() {
        saveChanges();
        logic.onStateChanged();
    }

    private void onUpgradesChanged() {
        saveChanges();
        logic.onStateChanged();
    }
}


