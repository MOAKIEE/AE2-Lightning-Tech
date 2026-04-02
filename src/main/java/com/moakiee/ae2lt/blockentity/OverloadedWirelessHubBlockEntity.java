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
import com.moakiee.ae2lt.menu.OverloadedWirelessHubMenu;
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

public class OverloadedWirelessHubBlockEntity extends AENetworkedBlockEntity
        implements OverloadedGridNodeOwner, IActionHost, IUpgradeableObject {

    public static final int MAX_PORT = 8;
    private static final String TAG_FREQUENCIES = "Frequencies";
    private static final String TAG_UPGRADES = "Upgrades";
    public static final int UPGRADE_SLOTS = 4;

    private final long[] frequencies = new long[MAX_PORT];
    private final WirelessConnect[] connects = new WirelessConnect[MAX_PORT];
    private final boolean[] needsUpdate = new boolean[MAX_PORT];
    private final IUpgradeInventory upgrades;
    private double powerUse;

    public OverloadedWirelessHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVERLOADED_WIRELESS_HUB.get(), pos, state);
        this.upgrades = UpgradeInventories.forMachine(
                ModBlocks.OVERLOADED_WIRELESS_HUB.get(),
                UPGRADE_SLOTS,
                this::onUpgradesChanged);
        for (int i = 0; i < MAX_PORT; i++) {
            connects[i] = new WirelessConnect(new PortNode(i));
        }
        getMainNode()
                .setFlags(GridFlags.DENSE_CAPACITY)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(AE2LTCommonConfig.wirelessConnectorPowerMultiplier())
                .setVisualRepresentation(ModBlocks.OVERLOADED_WIRELESS_HUB.get());
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

    // ── Port management ────────────────────────────────────────────────

    public int allocatePort() {
        for (int i = 0; i < MAX_PORT; i++) {
            if (frequencies[i] == 0 || !connects[i].isConnected()) {
                return i;
            }
        }
        return -1;
    }

    public void setFrequency(long freq, int port) {
        if (port < 0 || port >= MAX_PORT) {
            return;
        }
        if (this.frequencies[port] != freq) {
            this.frequencies[port] = freq;
            this.needsUpdate[port] = true;
            if (freq != 0) {
                FreqGenerator.INSTANCE.markUsed(Math.abs(freq));
            }
            saveChanges();
            markForClientUpdate();
        }
    }

    public long getFrequency(int port) {
        if (port < 0 || port >= MAX_PORT) {
            return 0;
        }
        return frequencies[port];
    }

    public void killPort(int port) {
        if (port < 0 || port >= MAX_PORT) {
            return;
        }
        frequencies[port] = 0;
        needsUpdate[port] = true;
        saveChanges();
        markForClientUpdate();
    }

    public WirelessStatus getStatus(int port) {
        if (port < 0 || port >= MAX_PORT) {
            return WirelessStatus.UNCONNECTED;
        }
        return connects[port].getStatus();
    }

    @Nullable
    public BlockPos getRemotePosition(int port) {
        if (port < 0 || port >= MAX_PORT) {
            return null;
        }
        var remote = connects[port].getRemote();
        return remote != null ? remote.getBlockPos() : null;
    }

    public int getRemoteChannels(int port) {
        if (port < 0 || port >= MAX_PORT) {
            return 0;
        }
        var remote = connects[port].getRemote();
        if (remote == null) {
            return 0;
        }
        var node = remote.getGridNode();
        return node != null ? node.getUsedChannels() : 0;
    }

    public int getUsedChannels() {
        var node = getMainNode().getNode();
        return node != null ? node.getUsedChannels() : 0;
    }

    public int getMaxChannels() {
        var node = getMainNode().getNode();
        return node != null ? node.getMaxChannels() : 0;
    }

    public double getPowerUse() {
        return powerUse;
    }

    // ── Tick ───────────────────────────────────────────────────────────

    public void serverTick() {
        boolean changed = false;
        for (int i = 0; i < MAX_PORT; i++) {
            // Retry if frequency set but not connected.
            if (!needsUpdate[i] && frequencies[i] != 0 && !connects[i].isConnected()) {
                needsUpdate[i] = true;
            }
            if (needsUpdate[i]) {
                needsUpdate[i] = false;
                connects[i].updateStatus();
                changed = true;
            }
        }
        if (changed) {
            updatePowerUsage();
            markForClientUpdate();
        }
    }

    public void requestUpdateAll() {
        for (int i = 0; i < MAX_PORT; i++) {
            needsUpdate[i] = true;
        }
    }

    // ── Power ──────────────────────────────────────────────────────────

    private void updatePowerUsage() {
        double discount = calculateDiscount();
        double multiplier = AE2LTCommonConfig.wirelessConnectorPowerMultiplier();
        this.powerUse = 0;
        boolean anyRunning = false;
        for (int i = 0; i < MAX_PORT; i++) {
            if (connects[i].isConnected()) {
                double dis = Math.max(connects[i].getDistance(), Math.E);
                this.powerUse += Math.max(1.0, dis * Math.log(dis) * discount) * multiplier;
                anyRunning = true;
            }
        }
        if (!anyRunning) {
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
        MenuOpener.open(OverloadedWirelessHubMenu.TYPE, player, locator);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onReady() {
        super.onReady();
        for (int i = 0; i < MAX_PORT; i++) {
            if (frequencies[i] != 0) {
                FreqGenerator.INSTANCE.markUsed(Math.abs(frequencies[i]));
            }
        }
        requestUpdateAll();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        for (var c : connects) {
            c.destroy();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        for (var c : connects) {
            c.destroy();
        }
    }

    // ── NBT ────────────────────────────────────────────────────────────

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        long[] freqs = new long[MAX_PORT];
        System.arraycopy(frequencies, 0, freqs, 0, MAX_PORT);
        data.putLongArray(TAG_FREQUENCIES, freqs);
        upgrades.writeToNBT(data, TAG_UPGRADES, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        if (data.contains(TAG_FREQUENCIES)) {
            long[] loaded = data.getLongArray(TAG_FREQUENCIES);
            for (int i = 0; i < MAX_PORT && i < loaded.length; i++) {
                frequencies[i] = loaded[i];
            }
        }
        upgrades.readFromNBT(data, TAG_UPGRADES, registries);
        requestUpdateAll();
    }

    // ── Network sync ───────────────────────────────────────────────────

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeDouble(powerUse);
        data.writeVarInt(getUsedChannels());
        data.writeVarInt(getMaxChannels());
        for (int i = 0; i < MAX_PORT; i++) {
            data.writeByte(connects[i].getStatus().ordinal());
            var remotePos = getRemotePosition(i);
            data.writeBoolean(remotePos != null);
            if (remotePos != null) {
                data.writeLong(remotePos.asLong());
            }
            data.writeVarInt(getRemoteChannels(i));
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        data.readDouble(); // powerUse
        data.readVarInt(); // usedChannels
        data.readVarInt(); // maxChannels
        for (int i = 0; i < MAX_PORT; i++) {
            data.readByte();
            boolean hasRemote = data.readBoolean();
            if (hasRemote) {
                data.readLong();
            }
            data.readVarInt();
        }
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
        return ModBlocks.OVERLOADED_WIRELESS_HUB.get().asItem();
    }

    private void onUpgradesChanged() {
        updatePowerUsage();
        saveChanges();
        markForClientUpdate();
    }

    // ── Virtual WirelessNode per port ──────────────────────────────────

    private class PortNode implements WirelessNode {
        private final int port;

        PortNode(int port) {
            this.port = port;
        }

        @Override
        public long getFrequency() {
            return frequencies[port];
        }

        @Override
        public Level getLevel() {
            return OverloadedWirelessHubBlockEntity.this.level;
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
            return OverloadedWirelessHubBlockEntity.this;
        }
    }
}
