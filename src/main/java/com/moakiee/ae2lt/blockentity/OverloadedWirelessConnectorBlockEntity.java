package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridNode;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.IActionHost;
import appeng.api.orientation.BlockOrientation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.machine.wireless.FreqGenerator;
import com.moakiee.ae2lt.machine.wireless.WirelessConnect;
import com.moakiee.ae2lt.machine.wireless.WirelessNode;
import com.moakiee.ae2lt.machine.wireless.WirelessStatus;
import com.moakiee.ae2lt.menu.OverloadedWirelessConnectorMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class OverloadedWirelessConnectorBlockEntity extends AENetworkedBlockEntity
        implements OverloadedGridNodeOwner, IActionHost, WirelessNode, IUpgradeableObject {

    private static final String TAG_FREQUENCY = "Frequency";
    private static final String TAG_UPGRADES = "Upgrades";
    public static final int UPGRADE_SLOTS = 4;

    private long frequency;
    private final WirelessConnect connect;
    private final IUpgradeInventory upgrades;
    private double powerUse;
    private boolean needsUpdate = true;

    public OverloadedWirelessConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVERLOADED_WIRELESS_CONNECTOR.get(), pos, state);
        this.connect = new WirelessConnect(this);
        this.upgrades = UpgradeInventories.forMachine(
                ModBlocks.OVERLOADED_WIRELESS_CONNECTOR.get(),
                UPGRADE_SLOTS,
                this::onUpgradesChanged);
        getMainNode()
                .setFlags(GridFlags.DENSE_CAPACITY)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(AE2LTCommonConfig.wirelessConnectorPowerMultiplier())
                .setVisualRepresentation(ModBlocks.OVERLOADED_WIRELESS_CONNECTOR.get());
    }

    // ── WirelessNode ───────────────────────────────────────────────────

    @Override
    public long getFrequency() {
        return frequency;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public BlockPos getBlockPos() {
        return worldPosition;
    }

    @Override
    @Nullable
    public IGridNode getGridNode() {
        return getMainNode().getNode();
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    // ── IActionHost ────────────────────────────────────────────────────

    @Override
    @Nullable
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    // ── IUpgradeableObject ─────────────────────────────────────────────

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    // ── Grid ───────────────────────────────────────────────────────────

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    // ── Frequency management ───────────────────────────────────────────

    public void setFrequency(long freq) {
        if (this.frequency != freq) {
            this.frequency = freq;
            this.needsUpdate = true;
            if (freq != 0) {
                FreqGenerator.INSTANCE.markUsed(Math.abs(freq));
            }
            saveChanges();
            markForClientUpdate();
        }
    }

    // ── Connection accessors ───────────────────────────────────────────

    public WirelessConnect getConnect() {
        return connect;
    }

    public WirelessStatus getStatus() {
        return connect.getStatus();
    }

    public double getPowerUse() {
        return powerUse;
    }

    @Nullable
    public BlockPos getRemotePosition() {
        var remote = connect.getRemote();
        return remote != null ? remote.getBlockPos() : null;
    }

    public int getUsedChannels() {
        var node = getMainNode().getNode();
        if (node == null) {
            return 0;
        }
        return node.getUsedChannels();
    }

    public int getMaxChannels() {
        var node = getMainNode().getNode();
        if (node == null) {
            return 0;
        }
        return node.getMaxChannels();
    }

    // ── Tick ───────────────────────────────────────────────────────────

    public void serverTick() {
        // Retry if frequency set but not connected.
        if (!needsUpdate && frequency != 0 && !connect.isConnected()) {
            needsUpdate = true;
        }
        if (needsUpdate) {
            needsUpdate = false;
            connect.updateStatus();
            updatePowerUsage();
            markForClientUpdate();
        }
    }

    public void requestUpdate() {
        this.needsUpdate = true;
    }

    // ── Power ──────────────────────────────────────────────────────────

    private void updatePowerUsage() {
        double discount = calculateDiscount();
        double multiplier = AE2LTCommonConfig.wirelessConnectorPowerMultiplier();
        if (connect.isConnected()) {
            double dis = Math.max(connect.getDistance(), Math.E);
            this.powerUse = Math.max(1.0, dis * Math.log(dis) * discount) * multiplier;
        } else {
            this.powerUse = multiplier;
        }
        getMainNode().setIdlePowerUsage(this.powerUse);
    }

    private double calculateDiscount() {
        int energyCards = getInstalledUpgrades(AEItems.ENERGY_CARD);
        return Math.max(0.1, 1.0 - 0.1 * energyCards);
    }

    // ── Menu ───────────────────────────────────────────────────────────

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(OverloadedWirelessConnectorMenu.TYPE, player, locator);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onReady() {
        super.onReady();
        if (frequency != 0) {
            FreqGenerator.INSTANCE.markUsed(Math.abs(frequency));
        }
        requestUpdate();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        connect.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        connect.destroy();
    }

    // ── NBT ────────────────────────────────────────────────────────────

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putLong(TAG_FREQUENCY, frequency);
        upgrades.writeToNBT(data, TAG_UPGRADES, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        // Support old EAE format "freq" tag as well.
        if (data.contains(TAG_FREQUENCY)) {
            frequency = data.getLong(TAG_FREQUENCY);
        } else if (data.contains("freq")) {
            frequency = data.getLong("freq");
        }
        upgrades.readFromNBT(data, TAG_UPGRADES, registries);
        needsUpdate = true;
    }

    // ── Network sync ───────────────────────────────────────────────────

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(connect.getStatus().ordinal());
        data.writeDouble(powerUse);
        data.writeVarInt(getUsedChannels());
        data.writeVarInt(getMaxChannels());
        var remotePos = getRemotePosition();
        data.writeBoolean(remotePos != null);
        if (remotePos != null) {
            data.writeLong(remotePos.asLong());
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);

        int statusOrd = data.readByte();
        double newPower = data.readDouble();
        int newUsed = data.readVarInt();
        int newMax = data.readVarInt();
        boolean hasRemote = data.readBoolean();
        long remoteAsLong = hasRemote ? data.readLong() : 0;

        // We store client-side sync data for the menu/screen.
        // Changes are detected by the screen itself.
        return changed;
    }

    // ── Drops ──────────────────────────────────────────────────────────

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        for (int i = 0; i < upgrades.size(); i++) {
            upgrades.setItemDirect(i, ItemStack.EMPTY);
        }
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.OVERLOADED_WIRELESS_CONNECTOR.get().asItem();
    }

    private void onUpgradesChanged() {
        updatePowerUsage();
        saveChanges();
        markForClientUpdate();
    }
}
