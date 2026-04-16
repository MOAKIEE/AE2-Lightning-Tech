package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

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
import com.moakiee.ae2lt.grid.WirelessTransmitterManager;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * Wireless receiver that creates a virtual {@link GridConnection} to a remote transmitter.
 * The binding UUID is set by the wireless link tool or wireless link card.
 * Connection management is event-driven: via {@link IGridNodeListener} for grid
 * changes and via {@link WirelessTransmitterManager.TransmitterListener} for
 * transmitter availability changes (card insert/remove).
 */
public class WirelessReceiverBlockEntity extends AENetworkedBlockEntity
        implements OverloadedGridNodeOwner, WirelessTransmitterManager.TransmitterListener, ServerTickingBlockEntity {

    private static final Logger LOG = LoggerFactory.getLogger("ae2lt-wireless");

    @Nullable
    private UUID boundTransmitterId;
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

    // ── Binding ──

    @Nullable
    public UUID getBoundTransmitterId() {
        return boundTransmitterId;
    }

    public void bindToTransmitter(UUID transmitterId) {
        if (transmitterId.equals(this.boundTransmitterId)) return;

        unsubscribeListener();
        destroyVirtualConnection();
        this.boundTransmitterId = transmitterId;
        subscribeListener();
        saveChanges();
        needsConnectionUpdate = true;
    }

    public void unbind() {
        unsubscribeListener();
        destroyVirtualConnection();
        this.boundTransmitterId = null;
        saveChanges();
        markForUpdate();
    }

    // ── Transmitter Listener ──

    @Override
    public void onTransmitterChanged(UUID uuid, boolean available) {
        if (!uuid.equals(boundTransmitterId)) return;
        needsConnectionUpdate = true;
    }

    private void subscribeListener() {
        if (boundTransmitterId == null) return;
        var manager = WirelessTransmitterManager.get();
        if (manager != null) {
            manager.addListener(boundTransmitterId, this);
        }
    }

    private void unsubscribeListener() {
        if (boundTransmitterId == null) return;
        var manager = WirelessTransmitterManager.get();
        if (manager != null) {
            manager.removeListener(boundTransmitterId, this);
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

        if (boundTransmitterId == null || level == null || level.isClientSide()) return;

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
        if (boundTransmitterId == null || level == null || level.isClientSide()) return;
        if (virtualConnection != null) return;

        IGridNode myNode = getMainNode().getNode();
        if (myNode == null) return;

        var manager = WirelessTransmitterManager.get();
        if (manager == null) return;

        var entry = manager.find(boundTransmitterId);
        if (entry == null) return;

        if (!entry.advanced()) {
            if (!level.dimension().equals(entry.dimension())) {
                return;
            }
        }

        var server = ((ServerLevel) level).getServer();
        IGridNode remoteNode = manager.resolveNode(boundTransmitterId, server);
        if (remoteNode == null) return;

        for (var conn : myNode.getConnections()) {
            if (conn.getOtherSide(myNode) == remoteNode) {
                return;
            }
        }

        try {
            virtualConnection = GridConnection.create(myNode, remoteNode, null);
            LOG.debug("Virtual connection established: receiver@{} -> transmitter UUID={}",
                    worldPosition, boundTransmitterId);
            markForUpdate();
        } catch (IllegalStateException e) {
            LOG.warn("Virtual connection FAILED: receiver@{} -> transmitter UUID={}: {}",
                    worldPosition, boundTransmitterId, e.getMessage());
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
        if (boundTransmitterId == null || level == null || level.isClientSide()) return;
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

        var manager = WirelessTransmitterManager.get();
        if (manager == null) return;

        var server = ((ServerLevel) level).getServer();
        IGridNode currentTarget = manager.resolveNode(boundTransmitterId, server);

        if (currentTarget == null || connectedTarget != currentTarget) {
            destroyVirtualConnection();
        }
    }

    // ── Lifecycle ──

    @Override
    public void onReady() {
        super.onReady();
        subscribeListener();
        if (boundTransmitterId != null) {
            needsConnectionUpdate = true;
        }
    }

    @Override
    public void setRemoved() {
        unsubscribeListener();
        destroyVirtualConnection();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        subscribeListener();
        if (boundTransmitterId != null) {
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
        if (boundTransmitterId != null) {
            tag.putUUID("BoundTransmitter", boundTransmitterId);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.hasUUID("BoundTransmitter")) {
            boundTransmitterId = tag.getUUID("BoundTransmitter");
        }
    }
}
