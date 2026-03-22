package com.moakiee.ae2lt.blockentity;

import java.util.List;
import java.util.Optional;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.orientation.BlockOrientation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.storage.CompositeStorage;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.parts.automation.StackWorldBehaviors;

import com.moakiee.ae2lt.block.LightningSimulationChamberBlock;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberAutomationInventory;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberEnergyStorage;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberLogic;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationLockedRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeCandidate;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class LightningSimulationChamberBlockEntity extends AENetworkedBlockEntity
        implements IUpgradeableObject, IActionHost {
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_LOCKED_RECIPE = "LockedRecipe";
    private static final String TAG_UPGRADES = "Upgrades";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CONSUMED_ENERGY = "ConsumedEnergy";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_AUTO_EXPORT = "AutoExport";
    private static final String TAG_ALLOWED_OUTPUTS = "AllowedOutputs";

    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int ENERGY_RECEIVE_PER_OPERATION = 200_000;
    public static final int SPEED_CARD_SLOTS = 4;

    private final LightningSimulationChamberInventory inventory =
            new LightningSimulationChamberInventory(this::onInventoryChanged);
    private final LightningSimulationChamberAutomationInventory automationInventory =
            new LightningSimulationChamberAutomationInventory(inventory);
    private final LightningSimulationChamberEnergyStorage energyStorage =
            new LightningSimulationChamberEnergyStorage(ENERGY_CAPACITY, ENERGY_RECEIVE_PER_OPERATION, this::onEnergyChanged);
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get(), SPEED_CARD_SLOTS, this::onUpgradesChanged);
    private final LightningSimulationChamberLogic logic;
    @SuppressWarnings("UnstableApiUsage")
    private final HashMap<Direction, Map<AEKeyType, ExternalStorageStrategy>> exportStrategies = new HashMap<>();

    private LightningSimulationLockedRecipe lockedRecipe;
    private long consumedEnergy;
    private int processingTicksSpent;
    private boolean working;
    private boolean autoExport;
    private EnumSet<RelativeSide> allowedOutputs = EnumSet.noneOf(RelativeSide.class);

    public LightningSimulationChamberBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(), pos, blockState);
        this.logic = new LightningSimulationChamberLogic(this);
        this.getMainNode()
                .setIdlePowerUsage(0)
                .addService(IGridTickable.class, logic);
    }

    public LightningSimulationChamberInventory getInventory() {
        return inventory;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return energyStorage;
    }

    public LightningSimulationChamberEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public LightningSimulationChamberLogic getLogic() {
        return logic;
    }

    public Optional<LightningSimulationRecipeCandidate> findProcessableRecipe() {
        return LightningSimulationRecipeService.findFirstProcessable(getLevel(), inventory);
    }

    public Optional<RecipeHolder<LightningSimulationRecipe>> getCurrentProcessableRecipe() {
        return findProcessableRecipe().map(LightningSimulationRecipeCandidate::recipe);
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public int getProcessingTicksSpent() {
        return processingTicksSpent;
    }

    public double getProgress() {
        if (lockedRecipe == null || lockedRecipe.totalEnergy() <= 0) {
            return 0.0D;
        }

        return Math.min(1.0D, (double) consumedEnergy / (double) lockedRecipe.totalEnergy());
    }

    public void addConsumedEnergy(long amount) {
        if (amount <= 0) {
            return;
        }

        this.consumedEnergy = Math.min(Long.MAX_VALUE, this.consumedEnergy + amount);
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

    public Optional<LightningSimulationLockedRecipe> getLockedRecipe() {
        return Optional.ofNullable(lockedRecipe);
    }

    public Optional<LightningSimulationLockedRecipe> lockCurrentRecipe() {
        if (lockedRecipe != null) {
            return Optional.of(lockedRecipe);
        }

        Optional<LightningSimulationRecipeCandidate> candidate = findProcessableRecipe();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        lockedRecipe = LightningSimulationLockedRecipe.fromCandidate(candidate.get());
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

    public boolean isAutoExportEnabled() {
        return autoExport;
    }

    public void setAutoExportEnabled(boolean autoExport) {
        if (this.autoExport == autoExport) {
            return;
        }

        this.autoExport = autoExport;
        saveChanges();
        logic.onStateChanged();
    }

    public EnumSet<RelativeSide> getAllowedOutputs() {
        return allowedOutputs.isEmpty() ? EnumSet.noneOf(RelativeSide.class) : EnumSet.copyOf(allowedOutputs);
    }

    public void updateOutputSides(EnumSet<RelativeSide> allowedOutputs) {
        this.allowedOutputs = allowedOutputs.isEmpty()
                ? EnumSet.noneOf(RelativeSide.class)
                : EnumSet.copyOf(allowedOutputs);
        saveChanges();
        logic.onStateChanged();
    }

    public boolean hasAutoExportWork() {
        return autoExport && !inventory.getStackInSlot(LightningSimulationChamberInventory.SLOT_OUTPUT).isEmpty();
    }

    public boolean pushOutResult() {
        if (!hasAutoExportWork() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        var orientation = getOrientation();
        for (var side : allowedOutputs) {
            var target = getExportTarget(serverLevel, orientation.getSide(side));
            if (target == null) {
                continue;
            }

            ItemStack output = inventory.getStackInSlot(LightningSimulationChamberInventory.SLOT_OUTPUT);
            if (output.isEmpty()) {
                return false;
            }

            var key = AEItemKey.of(output);
            if (key == null) {
                continue;
            }

            ItemStack extracted = inventory.extractItem(
                    LightningSimulationChamberInventory.SLOT_OUTPUT,
                    output.getCount(),
                    false);
            long inserted = target.insert(key, extracted.getCount(), Actionable.MODULATE, IActionSource.ofMachine(this));

            if (inserted < extracted.getCount()) {
                ItemStack remainder = extracted.copyWithCount((int) (extracted.getCount() - inserted));
                inventory.insertRecipeOutput(remainder, false);
            }

            if (inserted > 0) {
                return true;
            }
        }

        return false;
    }

    public boolean completeLockedRecipe(LightningSimulationRecipeCandidate candidate) {
        if (!inventory.canAcceptRecipeOutput(candidate.recipe().value().getResultStack())) {
            return false;
        }

        for (int slot = LightningSimulationChamberInventory.SLOT_INPUT_0;
             slot <= LightningSimulationChamberInventory.SLOT_INPUT_2;
             slot++) {
            int toConsume = candidate.match().getConsumptionForSlot(slot);
            if (toConsume <= 0) {
                continue;
            }

            if (inventory.getStackInSlot(slot).getCount() < toConsume) {
                return false;
            }
        }

        ItemStack catalyst = inventory.getStackInSlot(LightningSimulationChamberInventory.SLOT_OVERLOAD_DUST);
        if (catalyst.isEmpty()) {
            return false;
        }
        boolean consumesDust = inventory.isOverloadCrystalDust(catalyst);
        if (consumesDust && catalyst.getCount() < LightningSimulationRecipeService.REQUIRED_OVERLOAD_DUST) {
            return false;
        }
        if (!consumesDust && !inventory.isLightningCollapseMatrix(catalyst)) {
            return false;
        }

        for (int slot = LightningSimulationChamberInventory.SLOT_INPUT_0;
             slot <= LightningSimulationChamberInventory.SLOT_INPUT_2;
             slot++) {
            int toConsume = candidate.match().getConsumptionForSlot(slot);
            if (toConsume <= 0) {
                continue;
            }

            ItemStack extracted = inventory.extractItem(slot, toConsume, false);
            if (extracted.getCount() != toConsume) {
                return false;
            }
        }

        if (consumesDust) {
            ItemStack dustExtracted = inventory.extractItem(
                    LightningSimulationChamberInventory.SLOT_OVERLOAD_DUST,
                    LightningSimulationRecipeService.REQUIRED_OVERLOAD_DUST,
                    false);
            if (dustExtracted.getCount() != LightningSimulationRecipeService.REQUIRED_OVERLOAD_DUST) {
                return false;
            }
        }

        if (!inventory.insertRecipeOutput(candidate.recipe().value().getResultStack(), false).isEmpty()) {
            return false;
        }

        clearLockedRecipe();
        resetProgressState();
        setWorking(false);
        pushOutResult();
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(LightningSimulationChamberMenu.TYPE, player, locator);
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        boolean changed = this.working != working;
        this.working = working;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(LightningSimulationChamberBlock.WORKING)
                    && state.getValue(LightningSimulationChamberBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(LightningSimulationChamberBlock.WORKING, working), Block.UPDATE_ALL);
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

    private void onInventoryChanged() {
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

    @SuppressWarnings("UnstableApiUsage")
    private CompositeStorage getExportTarget(ServerLevel level, Direction direction) {
        if (exportStrategies.get(direction) == null) {
            exportStrategies.put(
                    direction,
                    StackWorldBehaviors.createExternalStorageStrategies(
                            level,
                            worldPosition.relative(direction),
                            direction.getOpposite()));
        }

        var externalStorages = new IdentityHashMap<AEKeyType, appeng.api.storage.MEStorage>(2);
        for (var entry : exportStrategies.get(direction).entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, () -> {});
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }

        if (!externalStorages.isEmpty()) {
            return new CompositeStorage(externalStorages);
        }

        return null;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        inventory.saveToTag(data, TAG_INVENTORY, registries);
        upgrades.writeToNBT(data, TAG_UPGRADES, registries);
        data.putLong(TAG_ENERGY, energyStorage.getStoredEnergyLong());
        data.putLong(TAG_CONSUMED_ENERGY, consumedEnergy);
        data.putInt(TAG_PROCESSING_TICKS, processingTicksSpent);
        data.putBoolean(TAG_AUTO_EXPORT, autoExport);
        ListTag outputTags = new ListTag();
        for (var side : allowedOutputs) {
            outputTags.add(StringTag.valueOf(side.name()));
        }
        data.put(TAG_ALLOWED_OUTPUTS, outputTags);
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
        consumedEnergy = Math.max(0L, data.getLong(TAG_CONSUMED_ENERGY));
        processingTicksSpent = Math.max(0, data.getInt(TAG_PROCESSING_TICKS));
        autoExport = data.getBoolean(TAG_AUTO_EXPORT);
        allowedOutputs.clear();
        ListTag outputTags = data.getList(TAG_ALLOWED_OUTPUTS, Tag.TAG_STRING);
        for (int i = 0; i < outputTags.size(); i++) {
            allowedOutputs.add(RelativeSide.valueOf(outputTags.getString(i)));
        }
        if (data.contains(TAG_LOCKED_RECIPE, Tag.TAG_COMPOUND)) {
            lockedRecipe = LightningSimulationLockedRecipe.fromTag(data.getCompound(TAG_LOCKED_RECIPE), registries);
        } else {
            lockedRecipe = null;
        }

        if (lockedRecipe == null) {
            consumedEnergy = 0L;
            processingTicksSpent = 0;
        } else {
            consumedEnergy = Math.min(consumedEnergy, lockedRecipe.totalEnergy());
        }
        working = lockedRecipe != null;
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        for (int slot = LightningSimulationChamberInventory.SLOT_INPUT_0;
             slot <= LightningSimulationChamberInventory.SLOT_INPUT_2;
             slot++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(data, inventory.getStackInSlot(slot));
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        for (int slot = LightningSimulationChamberInventory.SLOT_INPUT_0;
             slot <= LightningSimulationChamberInventory.SLOT_INPUT_2;
             slot++) {
            ItemStack oldStack = inventory.getStackInSlot(slot);
            ItemStack newStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
            if (!ItemStack.matches(oldStack, newStack)) {
                inventory.setClientRenderStack(slot, newStack);
                changed = true;
            }
        }
        return changed;
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
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }
}

