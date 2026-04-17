package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IManagedGridNode;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.logic.OverloadedPowerSupplyLogic;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.menu.OverloadedPowerSupplyMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class OverloadedPowerSupplyBlockEntity extends AENetworkedBlockEntity
        implements InternalInventoryHost {

    private static final String TAG_MODE = "Mode";
    private static final String TAG_CONNECTIONS = "WirelessConnections";
    private static final String TAG_CELL_INV = "CellInv";

    public enum PowerMode {
        NORMAL,
        OVERLOAD;

        public PowerMode next() {
            return this == NORMAL ? OVERLOAD : NORMAL;
        }
    }

    public record WirelessConnection(
            ResourceKey<Level> dimension,
            BlockPos pos,
            Direction boundFace
    ) {
        private static final String TAG_DIM = "Dim";
        private static final String TAG_POS = "Pos";
        private static final String TAG_FACE = "Face";

        public boolean sameTarget(ResourceKey<Level> otherDim, BlockPos otherPos) {
            return dimension.equals(otherDim) && pos.equals(otherPos);
        }

        public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString(TAG_DIM, dimension.location().toString());
            tag.putLong(TAG_POS, pos.asLong());
            tag.putInt(TAG_FACE, boundFace.get3DDataValue());
            return tag;
        }

        public static WirelessConnection fromTag(CompoundTag tag) {
            var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(TAG_DIM)));
            var pos = BlockPos.of(tag.getLong(TAG_POS));
            var face = Direction.from3DDataValue(tag.getInt(TAG_FACE));
            return new WirelessConnection(dim, pos, face);
        }
    }

    private final AppEngInternalInventory cellInv = new AppEngInternalInventory(this, 1, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AppFluxBridge.isFluxCell(stack);
        }
    };

    private final List<WirelessConnection> connections = new ArrayList<>();
    private final OverloadedPowerSupplyLogic logic;

    private PowerMode mode = PowerMode.NORMAL;

    public OverloadedPowerSupplyBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OVERLOADED_POWER_SUPPLY.get(), pos, blockState);
        this.logic = new OverloadedPowerSupplyLogic(this);
        getMainNode()
                .setIdlePowerUsage(0.0D)
                .addService(appeng.api.networking.ticking.IGridTickable.class, logic);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("overloaded_power_supply")
                .setVisualRepresentation(ModBlocks.OVERLOADED_POWER_SUPPLY.get());
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.OVERLOADED_POWER_SUPPLY.get().asItem();
    }

    public AppEngInternalInventory getCellInventory() {
        return cellInv;
    }

    public ItemStack getInstalledCell() {
        return cellInv.getStackInSlot(0);
    }

    public long getBufferCapacity() {
        return AppFluxBridge.getFluxCellCapacity(getInstalledCell());
    }

    /**
     * Resolve the installed Flux Cell's {@link MEStorage} view, properly
     * bound to a {@link ISaveProvider} so mutations are persisted back to
     * the ItemStack and this BE is marked dirty.
     *
     * @return the cell storage, or {@code null} when no valid Flux Cell is
     *         installed.
     */
    @Nullable
    public MEStorage getInstalledCellStorage() {
        ItemStack stack = getInstalledCell();
        if (stack.isEmpty() || !AppFluxBridge.isFluxCell(stack)) {
            return null;
        }

        StorageCell cell = StorageCells.getCellInventory(stack, this::onCellInventoryChanged);
        return cell;
    }

    private void onCellInventoryChanged() {
        saveChanges();
        markForClientUpdate();
    }

    public PowerMode getMode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode.next();
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    public OverloadedPowerSupplyLogic getSupplyLogic() {
        return logic;
    }

    public void addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i).sameTarget(dimension, pos)) {
                var updated = new WirelessConnection(dimension, pos, face);
                if (connections.get(i).equals(updated)) {
                    return;
                }
                connections.set(i, updated);
                saveChanges();
                markForClientUpdate();
                logic.onStateChanged();
                return;
            }
        }

        connections.add(new WirelessConnection(dimension, pos, face));
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    public boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos) {
        boolean removed = connections.removeIf(connection -> connection.sameTarget(dimension, pos));
        if (removed) {
            saveChanges();
            markForClientUpdate();
            logic.onStateChanged();
        }
        return removed;
    }

    public List<WirelessConnection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public int clearInvalidConnections() {
        var server = getLevel() instanceof ServerLevel sl ? sl.getServer() : null;
        if (server == null) {
            return 0;
        }

        int removed = 0;
        Iterator<WirelessConnection> iterator = connections.iterator();
        while (iterator.hasNext()) {
            var connection = iterator.next();
            ServerLevel targetLevel = server.getLevel(connection.dimension());
            if (targetLevel == null) {
                iterator.remove();
                removed++;
                continue;
            }

            if (!targetLevel.isLoaded(connection.pos())) {
                continue;
            }

            var state = targetLevel.getBlockState(connection.pos());
            if (state.isAir() || targetLevel.getBlockEntity(connection.pos()) == null) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            saveChanges();
            markForClientUpdate();
            logic.onStateChanged();
        }
        return removed;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == cellInv) {
            logic.onStateChanged();
        }
    }

    @Override
    public boolean isClientSide() {
        return level != null && level.isClientSide();
    }

    @Override
    public void setRemoved() {
        logic.flushBufferToNetwork();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        logic.flushBufferToNetwork();
        super.onChunkUnloaded();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putString(TAG_MODE, mode.name());
        cellInv.writeToNBT(data, TAG_CELL_INV, registries);

        var list = new ListTag();
        for (var connection : connections) {
            list.add(connection.toTag());
        }
        data.put(TAG_CONNECTIONS, list);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);

        if (data.contains(TAG_MODE, Tag.TAG_STRING)) {
            try {
                mode = PowerMode.valueOf(data.getString(TAG_MODE));
            } catch (IllegalArgumentException ignored) {
                mode = PowerMode.NORMAL;
            }
        } else {
            mode = PowerMode.NORMAL;
        }

        cellInv.readFromNBT(data, TAG_CELL_INV, registries);
        connections.clear();
        if (data.contains(TAG_CONNECTIONS, Tag.TAG_LIST)) {
            var list = data.getList(TAG_CONNECTIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                connections.add(WirelessConnection.fromTag(list.getCompound(i)));
            }
        }

        logic.onStateChanged();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(mode.ordinal());
        data.writeVarInt(connections.size());
        for (var connection : connections) {
            data.writeResourceLocation(connection.dimension().location());
            data.writeBlockPos(connection.pos());
            data.writeByte(connection.boundFace().get3DDataValue());
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);

        int modeOrdinal = data.readByte();
        PowerMode newMode = modeOrdinal >= 0 && modeOrdinal < PowerMode.values().length
                ? PowerMode.values()[modeOrdinal]
                : PowerMode.NORMAL;

        int count = data.readVarInt();
        var newConnections = new ArrayList<WirelessConnection>(count);
        for (int i = 0; i < count; i++) {
            var dim = ResourceKey.create(Registries.DIMENSION, data.readResourceLocation());
            var pos = data.readBlockPos();
            var face = Direction.from3DDataValue(data.readByte());
            newConnections.add(new WirelessConnection(dim, pos, face));
        }

        if (newMode != mode || !newConnections.equals(connections)) {
            mode = newMode;
            connections.clear();
            connections.addAll(newConnections);
            logic.onStateChanged();
            changed = true;
        }

        return changed;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(OverloadedPowerSupplyMenu.TYPE, player, locator);
    }
}
