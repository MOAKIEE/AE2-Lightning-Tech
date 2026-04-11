package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;

import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.grid.WirelessTransmitterManager;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * Phase-1 single-block wireless transmitter placeholder.
 * Scans adjacent blocks for a {@link WirelessIdBlockEntity} to obtain a UUID,
 * then registers itself in the global {@link WirelessTransmitterManager}.
 * Provides the anchor {@link IGridNode} for virtual wireless connections.
 */
public class WirelessTransmitterBlockEntity extends AENetworkedBlockEntity
        implements OverloadedGridNodeOwner, WirelessTransmitterManager.WirelessTransmitterNodeProvider {

    @Nullable
    private UUID boundId;
    private boolean registered;

    public WirelessTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_TRANSMITTER.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("wireless_transmitter")
                .setVisualRepresentation(ModBlocks.WIRELESS_TRANSMITTER.get())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(10);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    // ── WirelessTransmitterNodeProvider ──

    /**
     * Returns the node that remote receivers should connect to.
     * Searches for any overloaded controller on the network and returns its node.
     * Returns {@code null} if no overloaded controller is present (wireless unavailable).
     */
    @Override
    @Nullable
    public IGridNode getWirelessGridNode() {
        IGridNode myNode = getMainNode().getNode();
        if (myNode == null) return null;

        IGrid grid = myNode.getGrid();
        if (grid == null) return null;

        for (var node : grid.getMachineNodes(ControllerBlockEntity.class)) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) {
                return node;
            }
        }

        return null;
    }

    /**
     * @return {@code true} if the transmitter is on a valid overloaded-only network.
     */
    public boolean hasValidNetwork() {
        return getWirelessGridNode() != null;
    }

    @Override
    @Nullable
    public UUID getTransmitterUUID() {
        return boundId;
    }

    // ── ID Block scanning ──

    public void scanForIdBlock() {
        if (level == null || level.isClientSide()) return;

        UUID found = null;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getBlockEntity(neighborPos) instanceof WirelessIdBlockEntity idBe) {
                UUID neighborId = idBe.getWirelessId();
                if (neighborId != null && !idBe.isError()) {
                    found = neighborId;
                    break;
                }
            }
        }

        if (found != null && !found.equals(boundId)) {
            unregisterFromManager();
            boundId = found;
            registerToManager();
        } else if (found == null && boundId != null) {
            unregisterFromManager();
            boundId = null;
        }
    }

    private void registerToManager() {
        if (boundId == null || level == null || level.isClientSide()) return;
        var manager = WirelessTransmitterManager.get();
        if (manager == null) return;

        IGridNode node = getMainNode().getNode();
        manager.register(boundId, level.dimension(), worldPosition, node, false);
        registered = true;
    }

    private void unregisterFromManager() {
        if (!registered || boundId == null) return;
        var manager = WirelessTransmitterManager.get();
        if (manager != null) {
            manager.unregister(boundId);
        }
        registered = false;
    }

    // ── Lifecycle ──

    @Override
    public void onReady() {
        super.onReady();
        scanForIdBlock();
    }

    public void onNeighborChanged() {
        scanForIdBlock();
    }

    @Override
    public void setRemoved() {
        unregisterFromManager();
        super.setRemoved();
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.WIRELESS_TRANSMITTER.get().asItem();
    }

    // ── Persistence ──

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundId != null) {
            tag.putUUID("BoundId", boundId);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.hasUUID("BoundId")) {
            boundId = tag.getUUID("BoundId");
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WirelessTransmitterBlockEntity be) {
        // Re-register after world load if needed
        if (!be.registered && be.boundId != null) {
            be.registerToManager();
        }
    }
}
