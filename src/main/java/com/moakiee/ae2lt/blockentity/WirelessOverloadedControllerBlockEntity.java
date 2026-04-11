package com.moakiee.ae2lt.blockentity;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.me.GridConnection;
import appeng.util.inv.AppEngInternalInventory;

import com.moakiee.ae2lt.grid.WirelessConnectionCapProvider;
import com.moakiee.ae2lt.grid.WirelessTransmitterManager;
import com.moakiee.ae2lt.item.WirelessIdCardItem;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * Wireless Overloaded Controller: an overloaded controller that also acts as a
 * wireless transmitter. Insert a {@link WirelessIdCardItem} into the internal
 * slot to register this controller in the global {@link WirelessTransmitterManager}.
 * Receivers with the matching UUID connect directly to this controller's grid node.
 */
public class WirelessOverloadedControllerBlockEntity extends OverloadedControllerBlockEntity
        implements WirelessTransmitterManager.WirelessTransmitterNodeProvider, WirelessConnectionCapProvider {

    private final AppEngInternalInventory cardInv = new AppEngInternalInventory(this, 1, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof WirelessIdCardItem && WirelessIdCardItem.hasCardId(stack);
        }
    };

    @Nullable
    private UUID registeredId;

    public WirelessOverloadedControllerBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.WIRELESS_OVERLOADED_CONTROLLER.get(), pos, blockState);
    }

    protected WirelessOverloadedControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
        if (inv == cardInv) {
            onCardChanged();
        }
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("wireless_overloaded_controller")
                .setVisualRepresentation(ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get());
    }

    /**
     * Whether this is an advanced wireless controller (cross-dimension, unlimited channels).
     * Overridden in the advanced subclass.
     */
    public boolean isAdvanced() {
        return false;
    }

    @Override
    public int getWirelessChannelCap(ChannelMode mode) {
        return isAdvanced() ? Integer.MAX_VALUE / 2 : 32 * mode.getCableCapacityFactor();
    }

    // ── WirelessTransmitterNodeProvider ──

    @Override
    @Nullable
    public IGridNode getWirelessGridNode() {
        return getMainNode().getNode();
    }

    @Override
    @Nullable
    public UUID getTransmitterUUID() {
        return getCardUUID();
    }

    // ── Card Inventory ──

    public AppEngInternalInventory getCardInventory() {
        return cardInv;
    }

    @Nullable
    public UUID getCardUUID() {
        ItemStack card = cardInv.getStackInSlot(0);
        if (card.isEmpty()) return null;
        return WirelessIdCardItem.getCardId(card);
    }

    private void onCardChanged() {
        UUID newId = getCardUUID();
        var manager = WirelessTransmitterManager.get();
        if (manager == null) return;

        if (registeredId != null && !registeredId.equals(newId)) {
            destroyAllVirtualConnections();
            manager.unregister(registeredId);
            registeredId = null;
        }

        if (newId != null && level != null) {
            manager.register(newId, level.dimension(), worldPosition, getMainNode().getNode(), isAdvanced());
            registeredId = newId;
        }

        saveChanges();
    }

    /**
     * Destroys all virtual (direction-less) connections on this controller's
     * grid node. Called when the ID card is removed to ensure receivers
     * in unloaded chunks don't leave orphaned connections.
     */
    private void destroyAllVirtualConnections() {
        IGridNode node = getMainNode().getNode();
        if (node == null) return;

        for (var conn : new java.util.ArrayList<>(node.getConnections())) {
            if (conn instanceof GridConnection gc && gc.getDirection(node) == null) {
                gc.destroy();
            }
        }
    }

    // ── Grid Node Events ──

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            updateManagerRegistration();
        }
    }

    private void updateManagerRegistration() {
        UUID id = getCardUUID();
        var manager = WirelessTransmitterManager.get();
        if (manager == null || id == null || level == null) return;

        manager.register(id, level.dimension(), worldPosition, getMainNode().getNode(), isAdvanced());
        registeredId = id;
    }

    // ── Lifecycle ──

    @Override
    public void onReady() {
        super.onReady();
        updateManagerRegistration();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int slot = 0; slot < cardInv.size(); slot++) {
            ItemStack stack = cardInv.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void setRemoved() {
        var manager = WirelessTransmitterManager.get();
        if (manager != null && registeredId != null) {
            manager.unregister(registeredId);
            registeredId = null;
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        updateManagerRegistration();
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get().asItem();
    }

    public static void wirelessServerTick(Level level, BlockPos pos, BlockState state,
                                          WirelessOverloadedControllerBlockEntity be) {
        OverloadedControllerBlockEntity.serverTick(level, pos, state, be);
    }

    // ── Persistence ──

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var cardTag = new CompoundTag();
        cardInv.writeToNBT(cardTag, "CardInv", registries);
        tag.merge(cardTag);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        cardInv.readFromNBT(tag, "CardInv", registries);
    }
}
