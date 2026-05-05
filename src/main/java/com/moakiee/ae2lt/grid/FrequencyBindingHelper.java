package com.moakiee.ae2lt.grid;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.me.GridConnection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;

/**
 * Shared receiver-side frequency binding. It mirrors the original
 * WirelessReceiver virtual-connection lifecycle, but can be attached to any
 * AE-networked block entity whose main node should join a wireless controller.
 */
public final class FrequencyBindingHelper implements WirelessFrequencyManager.TransmitterListener {
    public static final String TAG_FREQUENCY_ID = "FrequencyId";
    public static final String TAG_MEMORY_FREQUENCY = "Frequency";

    private static final Logger LOG = LoggerFactory.getLogger("ae2lt-wireless");

    private final FrequencyBindingHost host;

    private int frequencyId = -1;
    @Nullable
    private IGridConnection virtualConnection;
    private boolean needsConnectionUpdate;

    public FrequencyBindingHelper(FrequencyBindingHost host) {
        this.host = host;
    }

    public int getFrequencyId() {
        return frequencyId;
    }

    public void setFrequency(int newFreqId) {
        if (newFreqId == this.frequencyId) return;

        var be = host.getFrequencyBindingBlockEntity();
        var manager = WirelessFrequencyManager.get();

        unsubscribeListener();
        destroyVirtualConnection();
        if (manager != null && frequencyId > 0 && be.getLevel() != null) {
            manager.unregisterDevice(frequencyId, be.getLevel().dimension(), be.getBlockPos());
        }

        this.frequencyId = newFreqId;

        subscribeListener();
        registerDevice();
        host.saveFrequencyBindingChanges();
        host.markFrequencyBindingForUpdate();
        needsConnectionUpdate = true;
    }

    public void clearFrequency() {
        var be = host.getFrequencyBindingBlockEntity();
        var manager = WirelessFrequencyManager.get();

        unsubscribeListener();
        destroyVirtualConnection();
        if (manager != null && frequencyId > 0 && be.getLevel() != null) {
            manager.unregisterDevice(frequencyId, be.getLevel().dimension(), be.getBlockPos());
        }

        this.frequencyId = -1;
        host.saveFrequencyBindingChanges();
        host.markFrequencyBindingForUpdate();
    }

    @Override
    public void onTransmitterChanged(int freqId, boolean available) {
        if (freqId != frequencyId) return;

        if (!available) {
            var manager = WirelessFrequencyManager.get();
            if (manager != null && !manager.isFrequencyValid(freqId)) {
                var be = host.getFrequencyBindingBlockEntity();
                destroyVirtualConnection();
                unsubscribeListener();
                if (be.getLevel() != null) {
                    manager.unregisterDevice(freqId, be.getLevel().dimension(), be.getBlockPos());
                }
                this.frequencyId = -1;
                host.saveFrequencyBindingChanges();
                host.markFrequencyBindingForUpdate();
                return;
            }
        }

        needsConnectionUpdate = true;
    }

    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            needsConnectionUpdate = true;
        }
    }

    public void serverTick() {
        if (!needsConnectionUpdate) return;

        var be = host.getFrequencyBindingBlockEntity();
        if (be.getMainNode().getNode() == null) return;

        needsConnectionUpdate = false;

        if (frequencyId <= 0 || be.getLevel() == null || be.getLevel().isClientSide()) return;

        if (virtualConnection != null) {
            revalidateConnection();
        }

        if (virtualConnection == null) {
            tryEstablishConnection();
        }

        host.markFrequencyBindingForUpdate();
    }

    public void onReady() {
        if (frequencyId > 0) {
            var manager = WirelessFrequencyManager.get();
            if (manager != null && !manager.isFrequencyValid(frequencyId)) {
                frequencyId = -1;
                host.saveFrequencyBindingChanges();
                return;
            }
            registerDevice();
        }

        subscribeListener();
        if (frequencyId > 0) {
            needsConnectionUpdate = true;
        }
    }

    public void setRemoved() {
        var be = host.getFrequencyBindingBlockEntity();
        var manager = WirelessFrequencyManager.get();

        unsubscribeListener();
        destroyVirtualConnection();
        if (manager != null && frequencyId > 0 && be.getLevel() != null) {
            manager.unregisterDevice(frequencyId, be.getLevel().dimension(), be.getBlockPos());
        }
    }

    public void clearRemoved() {
        subscribeListener();
        registerDevice();
        if (frequencyId > 0) {
            needsConnectionUpdate = true;
        }
    }

    public void save(CompoundTag tag) {
        tag.putInt(TAG_FREQUENCY_ID, frequencyId);
    }

    public void load(CompoundTag tag) {
        frequencyId = tag.contains(TAG_FREQUENCY_ID) ? tag.getInt(TAG_FREQUENCY_ID) : -1;
    }

    public int getGridUsedChannels() {
        var grid = host.getFrequencyBindingBlockEntity().getMainNode().getGrid();
        if (grid == null) return 0;

        int count = 0;
        for (var node : grid.getNodes()) {
            if (node.hasFlag(GridFlags.REQUIRE_CHANNEL) && node.meetsChannelRequirements()) {
                count++;
            }
        }
        return count;
    }

    public int getGridMaxChannels() {
        var grid = host.getFrequencyBindingBlockEntity().getMainNode().getGrid();
        if (grid == null) return 0;

        var channelMode = grid.getPathingService().getChannelMode();
        if (channelMode == appeng.api.networking.pathing.ChannelMode.INFINITE) {
            return -1;
        }

        int overloadedCount = 0;
        int vanillaCount = 0;
        for (var node : OverloadedChannelOwnerHelper.getAllControllerNodes(grid)) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) {
                overloadedCount++;
            } else {
                vanillaCount++;
            }
        }

        int factor = Math.max(1, channelMode.getCableCapacityFactor());
        long cap = (long) overloadedCount * OverloadedChannelOwnerHelper.channelsPerController() * factor
                + (long) vanillaCount * 32L * factor;
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    public boolean isConnected() {
        if (virtualConnection == null) return false;

        IGridNode myNode = host.getFrequencyBindingBlockEntity().getMainNode().getNode();
        if (myNode == null) return false;
        for (var conn : myNode.getConnections()) {
            if (conn == virtualConnection) return true;
        }

        virtualConnection = null;
        return false;
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

    private void registerDevice() {
        if (frequencyId <= 0) return;

        var be = host.getFrequencyBindingBlockEntity();
        var manager = WirelessFrequencyManager.get();
        if (manager != null && be.getLevel() != null) {
            manager.registerDevice(frequencyId, new WirelessFrequencyManager.DeviceEntry(
                    be.getLevel().dimension(),
                    be.getBlockPos(),
                    false,
                    false,
                    host.getFrequencyBindingDeviceName()));
        }
    }

    private void tryEstablishConnection() {
        var be = host.getFrequencyBindingBlockEntity();
        if (frequencyId <= 0 || be.getLevel() == null || be.getLevel().isClientSide()) return;
        if (virtualConnection != null) return;

        IGridNode myNode = be.getMainNode().getNode();
        if (myNode == null) return;

        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;

        var entry = manager.findTransmitter(frequencyId);
        if (entry == null) return;

        if (!entry.advanced() && !be.getLevel().dimension().equals(entry.dimension())) {
            return;
        }

        var server = ((ServerLevel) be.getLevel()).getServer();
        IGridNode remoteNode = manager.resolveNode(frequencyId, server);
        if (remoteNode == null) return;

        for (var conn : myNode.getConnections()) {
            if (conn.getOtherSide(myNode) == remoteNode) {
                return;
            }
        }

        try {
            virtualConnection = GridConnection.create(myNode, remoteNode, null);
            LOG.debug("Virtual connection established: device@{} -> freq={}", be.getBlockPos(), frequencyId);
            host.markFrequencyBindingForUpdate();
        } catch (IllegalStateException e) {
            LOG.warn("Virtual connection FAILED: device@{} -> freq={}: {}",
                    be.getBlockPos(), frequencyId, e.getMessage());
        }
    }

    private void destroyVirtualConnection() {
        if (virtualConnection == null) return;

        IGridNode myNode = host.getFrequencyBindingBlockEntity().getMainNode().getNode();
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

    private void revalidateConnection() {
        var be = host.getFrequencyBindingBlockEntity();
        if (frequencyId <= 0 || be.getLevel() == null || be.getLevel().isClientSide()) return;
        if (virtualConnection == null) return;

        IGridNode myNode = be.getMainNode().getNode();
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

        var server = ((ServerLevel) be.getLevel()).getServer();
        IGridNode currentTarget = manager.resolveNode(frequencyId, server);
        if (currentTarget == null || connectedTarget != currentTarget) {
            destroyVirtualConnection();
        }
    }
}
