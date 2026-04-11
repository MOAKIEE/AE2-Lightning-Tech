package com.moakiee.ae2lt.blockentity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.moakiee.ae2lt.grid.WirelessTransmitterManager;
import com.moakiee.ae2lt.registry.ModBlockEntities;

/**
 * Holds a persistent UUID that identifies a wireless transmitter.
 * First placement generates a new UUID; breaking preserves it in the dropped item.
 * Only one block entity with a given UUID may be active at a time.
 */
public class WirelessIdBlockEntity extends BlockEntity {

    private static final String TAG_UUID = "WirelessId";

    @Nullable
    private UUID wirelessId;
    private boolean error;

    public WirelessIdBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_ID_BLOCK.get(), pos, state);
    }

    @Nullable
    public UUID getWirelessId() {
        return wirelessId;
    }

    public void setWirelessId(UUID id) {
        this.wirelessId = id;
        setChanged();
    }

    public boolean isError() {
        return error;
    }

    /**
     * Called from {@link com.moakiee.ae2lt.block.WirelessIdBlock#setPlacedBy}
     * to initialize the UUID (from item NBT or newly generated).
     */
    public void initializeId(@Nullable UUID fromItem) {
        if (fromItem != null) {
            this.wirelessId = fromItem;
        } else {
            this.wirelessId = UUID.randomUUID();
        }
        checkDuplicateAndRegister();
        setChanged();
    }

    private void checkDuplicateAndRegister() {
        if (wirelessId == null || level == null || level.isClientSide()) return;
        var manager = WirelessTransmitterManager.get();
        if (manager == null) return;

        if (manager.isActive(wirelessId)) {
            this.error = true;
        } else {
            this.error = false;
        }
    }

    public void onRemoved() {
        // ID block itself does not register to the manager;
        // the transmitter that reads this block handles registration.
        // But we need to notify neighbor transmitters.
        if (level != null && !level.isClientSide()) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (wirelessId != null) {
            tag.putUUID(TAG_UUID, wirelessId);
        }
        tag.putBoolean("Error", error);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_UUID)) {
            wirelessId = tag.getUUID(TAG_UUID);
        }
        error = tag.getBoolean("Error");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
