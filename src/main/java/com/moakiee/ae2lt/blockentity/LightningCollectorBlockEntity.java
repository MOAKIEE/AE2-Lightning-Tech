package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.machine.lightningcollector.LightningCollectorInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class LightningCollectorBlockEntity extends AENetworkedBlockEntity implements IActionHost {
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_COOLDOWN = "CooldownTicks";
    private static boolean warnedInvalidHighVoltageBaseRange;
    private static boolean warnedInvalidExtremeVoltageBaseRange;

    private final LightningCollectorInventory inventory = new LightningCollectorInventory(this::onInventoryChanged);

    private int cooldownTicks;

    public LightningCollectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LIGHTNING_COLLECTOR.get(), pos, blockState);
        getMainNode().setIdlePowerUsage(0.0D);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightningCollectorBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel)) {
            return;
        }

        if (blockEntity.cooldownTicks > 0) {
            blockEntity.cooldownTicks--;
            if (blockEntity.cooldownTicks == 0) {
                blockEntity.saveChanges();
                blockEntity.markForClientUpdate();
            }
        }
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return inventory;
    }

    public LightningCollectorInventory getInventory() {
        return inventory;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public ItemStack getInstalledCrystal() {
        return inventory.getStackInSlot(LightningCollectorInventory.SLOT_CRYSTAL);
    }

    public int getCatalysisValue() {
        ItemStack crystal = getInstalledCrystal();
        return crystal.isEmpty() ? 0 : ElectroChimeCrystalItem.getCatalysisValue(crystal);
    }

    public OutputPreview getPreview(LightningKey.Tier tier) {
        ItemStack crystal = getInstalledCrystal();
        boolean extremeHighVoltage = tier == LightningKey.Tier.EXTREME_HIGH_VOLTAGE;
        if (crystal.isEmpty()) {
            int baseMin = extremeHighVoltage
                    ? AE2LTCommonConfig.lightningCollectorEhvBaseMin()
                    : AE2LTCommonConfig.lightningCollectorHvBaseMin();
            int baseMax = extremeHighVoltage
                    ? AE2LTCommonConfig.lightningCollectorEhvBaseMax()
                    : AE2LTCommonConfig.lightningCollectorHvBaseMax();
            if (baseMin > baseMax) {
                warnInvalidBaseRange(extremeHighVoltage, baseMin, baseMax);
            }
            return new OutputPreview(
                    Math.min(baseMin, baseMax),
                    Math.max(baseMin, baseMax));
        }

        if (crystal.is(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get())) {
            int output = extremeHighVoltage
                    ? AE2LTCommonConfig.lightningCollectorPerfectEhvOutput()
                    : AE2LTCommonConfig.lightningCollectorPerfectHvOutput();
            return new OutputPreview(output, output);
        }

        double progress = ElectroChimeCrystalItem.getCatalysisPercent(crystal);
        double center = Mth.lerp(
                progress,
                extremeHighVoltage
                        ? AE2LTCommonConfig.lightningCollectorEhvCrystalStart()
                        : AE2LTCommonConfig.lightningCollectorHvCrystalStart(),
                extremeHighVoltage
                        ? AE2LTCommonConfig.lightningCollectorEhvCrystalEnd()
                        : AE2LTCommonConfig.lightningCollectorHvCrystalEnd());
        int spread = Math.max(1, Mth.floor(center * AE2LTCommonConfig.lightningCollectorSpreadRatio()));
        int min = Math.max(1, Mth.floor(center) - spread);
        int max = Math.max(min, Mth.ceil(center) + spread);
        return new OutputPreview(min, max);
    }

    public boolean canCaptureLightning() {
        return cooldownTicks <= 0;
    }

    public boolean captureLightning(boolean naturalWeatherLightning) {
        if (!(level instanceof ServerLevel serverLevel) || cooldownTicks > 0) {
            return false;
        }

        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        LightningKey.Tier tier = naturalWeatherLightning
                ? LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                : LightningKey.Tier.HIGH_VOLTAGE;
        OutputPreview preview = getPreview(tier);
        int rolledOutput = preview.roll(serverLevel.random);
        if (rolledOutput <= 0) {
            return false;
        }

        if (tier == LightningKey.Tier.EXTREME_HIGH_VOLTAGE) {
            cultivateCrystal(serverLevel.random);
        }
        long amountToInsert = rolledOutput;
        long inserted = amountToInsert > 0
                ? grid.getStorageService().getInventory().insert(
                        LightningKey.of(tier),
                        amountToInsert,
                        Actionable.MODULATE,
                        IActionSource.ofMachine(this))
                : 0L;

        boolean captured = inserted > 0;
        if (!captured) {
            return false;
        }

        this.cooldownTicks = AE2LTCommonConfig.lightningCollectorCooldownTicks();
        saveChanges();
        markForClientUpdate();
        return true;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(LightningCollectorMenu.TYPE, player, locator);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        inventory.saveToTag(data, TAG_INVENTORY, registries);
        data.putInt(TAG_COOLDOWN, cooldownTicks);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        inventory.loadFromTag(data, TAG_INVENTORY, registries);
        cooldownTicks = Math.max(0, data.getInt(TAG_COOLDOWN));
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(data, getInstalledCrystal());
        data.writeVarInt(cooldownTicks);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        ItemStack oldCrystal = getInstalledCrystal();
        ItemStack newCrystal = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
        if (!ItemStack.matches(oldCrystal, newCrystal)) {
            inventory.setClientRenderStack(newCrystal);
            changed = true;
        }

        int newCooldown = data.readVarInt();
        if (newCooldown != cooldownTicks) {
            cooldownTicks = newCooldown;
            changed = true;
        }
        return changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack crystal = getInstalledCrystal();
        if (!crystal.isEmpty()) {
            drops.add(crystal.copy());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        inventory.clear();
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.LIGHTNING_COLLECTOR.get().asItem();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    private void cultivateCrystal(RandomSource random) {
        ItemStack crystal = getInstalledCrystal();
        if (crystal.isEmpty() || crystal.is(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get())) {
            return;
        }

        int feed = ElectroChimeCrystalItem.rollCatalysisFeed(random);
        int catalysis = ElectroChimeCrystalItem.addCatalysis(crystal, feed);
        if (catalysis >= ElectroChimeCrystalItem.getMaxCatalysis()) {
            inventory.setStackInSlot(LightningCollectorInventory.SLOT_CRYSTAL,
                    new ItemStack(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get()));
        }
    }

    private static void warnInvalidBaseRange(boolean extremeHighVoltage, int min, int max) {
        if (extremeHighVoltage) {
            if (warnedInvalidExtremeVoltageBaseRange) {
                return;
            }
            warnedInvalidExtremeVoltageBaseRange = true;
        } else {
            if (warnedInvalidHighVoltageBaseRange) {
                return;
            }
            warnedInvalidHighVoltageBaseRange = true;
        }

        LOG.warn(
                "Invalid lightning collector {} base output range: min={} max={}. Swapping values as a fallback.",
                extremeHighVoltage ? "extremeHighVoltage" : "highVoltage",
                min,
                max);
    }

    private void onInventoryChanged() {
        saveChanges();
        markForClientUpdate();
    }

    public record OutputPreview(int min, int max) {
        public int roll(RandomSource random) {
            if (max <= min) {
                return min;
            }
            return random.nextInt(max - min + 1) + min;
        }
    }
}
