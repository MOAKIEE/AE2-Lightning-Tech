package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.GridConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * Wireless receiver that creates a virtual {@link GridConnection} to a remote transmitter.
 * The binding is done via a frequency ID selected through the GUI.
 * Connection management is event-driven: via {@link IGridNodeListener} for grid
 * changes and via {@link WirelessFrequencyManager.TransmitterListener} for
 * transmitter availability changes.
 */
public class WirelessReceiverBlockEntity extends AENetworkedBlockEntity
        implements OverloadedGridNodeOwner, WirelessFrequencyManager.TransmitterListener, ServerTickingBlockEntity {

    private static final Logger LOG = LoggerFactory.getLogger("ae2lt-wireless");

    private int frequencyId = -1;
    @Nullable
    private IGridConnection virtualConnection;

    private boolean needsConnectionUpdate;

    public WirelessReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_RECEIVER.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("wireless_receiver")
                .setVisualRepresentation(ModBlocks.WIRELESS_RECEIVER.get())
                .setIdlePowerUsage(5);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    // ── Frequency Binding ──

    public int getFrequencyId() {
        return frequencyId;
    }

    public int getGridUsedChannels() {
        var grid = getMainNode().getGrid();
        if (grid == null) return 0;
        int count = 0;
        for (var node : grid.getNodes()) {
            if (node.hasFlag(appeng.api.networking.GridFlags.REQUIRE_CHANNEL)
                    && node.meetsChannelRequirements()) {
                count++;
            }
        }
        return count;
    }

    public int getGridMaxChannels() {
        var grid = getMainNode().getGrid();
        if (grid == null) return 0;

        var channelMode = grid.getPathingService().getChannelMode();
        if (channelMode == appeng.api.networking.pathing.ChannelMode.INFINITE) {
            return -1;
        }

        int overloadedCount = 0;
        int vanillaCount = 0;
        for (var node : com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper.getAllControllerNodes(grid)) {
            if (node.getOwner() instanceof com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity) {
                overloadedCount++;
            } else {
                vanillaCount++;
            }
        }
        int factor = Math.max(1, channelMode.getCableCapacityFactor());
        long cap = (long) overloadedCount
                * com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper.channelsPerController() * factor
                + (long) vanillaCount * 32L * factor;
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    public void setFrequency(int newFreqId) {
        if (newFreqId == this.frequencyId) return;

        var manager = WirelessFrequencyManager.get();

        unsubscribeListener();
        destroyVirtualConnection();
        if (manager != null && frequencyId > 0 && level != null) {
            manager.unregisterDevice(frequencyId, level.dimension(), worldPosition);
        }
        this.frequencyId = newFreqId;
        subscribeListener();
        if (manager != null && frequencyId > 0 && level != null) {
            manager.registerDevice(frequencyId, new WirelessFrequencyManager.DeviceEntry(
                    level.dimension(), worldPosition, false, false));
        }
        saveChanges();
        needsConnectionUpdate = true;
    }

    public void clearFrequency() {
        var manager = WirelessFrequencyManager.get();
        unsubscribeListener();
        destroyVirtualConnection();
        if (manager != null && frequencyId > 0 && level != null) {
            manager.unregisterDevice(frequencyId, level.dimension(), worldPosition);
        }
        this.frequencyId = -1;
        saveChanges();
        markForUpdate();
    }

    // ── Transmitter Listener ──

    @Override
    public void onTransmitterChanged(int freqId, boolean available) {
        if (freqId != frequencyId) return;
        if (!available) {
            // check if the frequency itself was deleted (not just transmitter offline)
            var manager = WirelessFrequencyManager.get();
            if (manager != null && !manager.isFrequencyValid(freqId)) {
                // frequency deleted — fully unbind
                destroyVirtualConnection();
                unsubscribeListener();
                if (level != null) {
                    manager.unregisterDevice(freqId, level.dimension(), worldPosition);
                }
                this.frequencyId = -1;
                saveChanges();
                markForUpdate();
                return;
            }
        }
        needsConnectionUpdate = true;
    }

    private void subscribeListener() {
        if (frequencyId <= 0) return;
        var manager = WirelessFrequencyManager.get();
        if (manager != null) {
            manager.addListener(frequencyId, this);
        }
    }

    private void unsubscribeListener() {
        if (frequencyId <= 0) return;
        var manager = WirelessFrequencyManager.get();
        if (manager != null) {
            manager.removeListener(frequencyId, this);
        }
    }

    // ── Grid Node Events ──

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            needsConnectionUpdate = true;
        }
    }

    // ── Server Tick (deferred connection management) ──

    @Override
    public void serverTick() {
        if (!needsConnectionUpdate) return;

        if (getMainNode().getNode() == null) return;

        needsConnectionUpdate = false;

        if (frequencyId <= 0 || level == null || level.isClientSide()) return;

        if (virtualConnection != null) {
            revalidateConnection();
        }

        if (virtualConnection == null) {
            tryEstablishConnection();
        }

        markForUpdate();
    }

    // ── Virtual Connection ──

    private void tryEstablishConnection() {
        if (frequencyId <= 0 || level == null || level.isClientSide()) return;
        if (virtualConnection != null) return;

        IGridNode myNode = getMainNode().getNode();
        if (myNode == null) return;

        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;

        var entry = manager.findTransmitter(frequencyId);
        if (entry == null) return;

        if (!entry.advanced()) {
            if (!level.dimension().equals(entry.dimension())) {
                return;
            }
        }

        var server = ((ServerLevel) level).getServer();
        IGridNode remoteNode = manager.resolveNode(frequencyId, server);
        if (remoteNode == null) return;

        for (var conn : myNode.getConnections()) {
            if (conn.getOtherSide(myNode) == remoteNode) {
                return;
            }
        }

        try {
            virtualConnection = GridConnection.create(myNode, remoteNode, null);
            LOG.debug("Virtual connection established: receiver@{} -> freq={}",
                    worldPosition, frequencyId);
            markForUpdate();
        } catch (IllegalStateException e) {
            LOG.warn("Virtual connection FAILED: receiver@{} -> freq={}: {}",
                    worldPosition, frequencyId, e.getMessage());
        }
    }

    private void destroyVirtualConnection() {
        if (virtualConnection == null) return;

        IGridNode myNode = getMainNode().getNode();
        if (myNode != null) {
            for (var conn : myNode.getConnections()) {
                if (conn == virtualConnection) {
                    virtualConnection.destroy();
                    break;
                }
            }
        }
        virtualConnection = null;
    }

    public boolean isConnected() {
        if (virtualConnection == null) return false;

        IGridNode myNode = getMainNode().getNode();
        if (myNode == null) return false;
        for (var conn : myNode.getConnections()) {
            if (conn == virtualConnection) return true;
        }

        virtualConnection = null;
        return false;
    }

    private void revalidateConnection() {
        if (frequencyId <= 0 || level == null || level.isClientSide()) return;
        if (virtualConnection == null) return;

        IGridNode myNode = getMainNode().getNode();
        if (myNode == null) return;

        boolean connectionAlive = false;
        IGridNode connectedTarget = null;
        for (var conn : myNode.getConnections()) {
            if (conn == virtualConnection) {
                connectionAlive = true;
                connectedTarget = conn.getOtherSide(myNode);
                break;
            }
        }

        if (!connectionAlive) {
            virtualConnection = null;
            return;
        }

        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;

        var server = ((ServerLevel) level).getServer();
        IGridNode currentTarget = manager.resolveNode(frequencyId, server);

        if (currentTarget == null || connectedTarget != currentTarget) {
            destroyVirtualConnection();
        }
    }

    // ── Lifecycle ──

    @Override
    public void onReady() {
        super.onReady();
        // validate frequency still exists; clear zombie binding
        if (frequencyId > 0) {
            var manager = WirelessFrequencyManager.get();
            if (manager != null && !manager.isFrequencyValid(frequencyId)) {
                frequencyId = -1;
                saveChanges();
                return;
            }
            if (manager != null && level != null) {
                manager.registerDevice(frequencyId, new WirelessFrequencyManager.DeviceEntry(
                        level.dimension(), worldPosition, false, false));
            }
        }
        subscribeListener();
        if (frequencyId > 0) {
            needsConnectionUpdate = true;
        }
    }

    @Override
    public void setRemoved() {
        var manager = WirelessFrequencyManager.get();
        unsubscribeListener();
        destroyVirtualConnection();
        if (manager != null && frequencyId > 0 && level != null) {
            manager.unregisterDevice(frequencyId, level.dimension(), worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        subscribeListener();
        var manager = WirelessFrequencyManager.get();
        if (manager != null && frequencyId > 0 && level != null) {
            manager.registerDevice(frequencyId, new WirelessFrequencyManager.DeviceEntry(
                    level.dimension(), worldPosition, false, false));
        }
        if (frequencyId > 0) {
            needsConnectionUpdate = true;
        }
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.WIRELESS_RECEIVER.get().asItem();
    }

    // ── Persistence ──

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FrequencyId", frequencyId);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        frequencyId = tag.contains("FrequencyId") ? tag.getInt("FrequencyId") : -1;
    }

    private static final String TAG_FREQUENCY = "Frequency";

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @Nullable net.minecraft.world.entity.player.Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == appeng.util.SettingsFrom.MEMORY_CARD && frequencyId > 0) {
            var tag = new CompoundTag();
            tag.putInt(TAG_FREQUENCY, frequencyId);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeCustomTag(builder, tag);
        }
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @Nullable net.minecraft.world.entity.player.Player player) {
        super.importSettings(mode, input, player);
        if (mode != appeng.util.SettingsFrom.MEMORY_CARD) {
            return;
        }
        var tag = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readCustomTag(input);
        if (tag == null || !tag.contains(TAG_FREQUENCY)) {
            return;
        }
        setFrequency(tag.getInt(TAG_FREQUENCY));
    }
}
