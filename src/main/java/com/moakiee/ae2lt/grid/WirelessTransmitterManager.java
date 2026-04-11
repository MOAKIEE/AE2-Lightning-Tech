package com.moakiee.ae2lt.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import appeng.api.networking.IGridNode;

/**
 * Global registry of wireless transmitters, keyed by their UUID from the ID Card.
 * Persisted as overworld SavedData so it survives server restarts.
 * At runtime, also caches a live {@link IGridNode} reference for fast lookup.
 *
 * Supports an observer mechanism: receivers can subscribe to transmitter
 * availability changes and get notified when a transmitter registers or
 * unregisters for their bound UUID.
 */
public final class WirelessTransmitterManager extends SavedData {

    private static final String DATA_NAME = "ae2lt_wireless_transmitters";

    public record TransmitterEntry(
            ResourceKey<Level> dimension,
            BlockPos pos,
            @Nullable IGridNode cachedNode,
            boolean advanced
    ) {}

    @FunctionalInterface
    public interface TransmitterListener {
        void onTransmitterChanged(UUID uuid, boolean available);
    }

    private final Map<UUID, TransmitterEntry> entries = new HashMap<>();
    private final Map<UUID, List<TransmitterListener>> listeners = new HashMap<>();

    @Nullable
    private static WirelessTransmitterManager instance;

    private WirelessTransmitterManager() {}

    private WirelessTransmitterManager(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = tag.getList("transmitters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID uuid = entry.getUUID("uuid");
            var dimKey = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getString("dim")));
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            boolean adv = entry.contains("advanced") && entry.getBoolean("advanced");
            entries.put(uuid, new TransmitterEntry(dimKey, pos, null, adv));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var e : entries.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", e.getKey());
            entry.putString("dim", e.getValue().dimension().location().toString());
            entry.putLong("pos", e.getValue().pos().asLong());
            entry.putBoolean("advanced", e.getValue().advanced());
            list.add(entry);
        }
        tag.put("transmitters", list);
        return tag;
    }

    // ── Lifecycle ──

    public static void onServerStart(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        instance = overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WirelessTransmitterManager::new,
                        WirelessTransmitterManager::new),
                DATA_NAME);
    }

    public static void onServerStop() {
        if (instance != null) {
            instance.listeners.clear();
        }
        instance = null;
    }

    @Nullable
    public static WirelessTransmitterManager get() {
        return instance;
    }

    // ── Registration ──

    public boolean isActive(UUID uuid) {
        return entries.containsKey(uuid);
    }

    public void register(UUID uuid, ResourceKey<Level> dimension, BlockPos pos, @Nullable IGridNode node, boolean advanced) {
        entries.put(uuid, new TransmitterEntry(dimension, pos, node, advanced));
        setDirty();
        fireListeners(uuid, true);
    }

    public void unregister(UUID uuid) {
        if (entries.remove(uuid) != null) {
            setDirty();
            fireListeners(uuid, false);
        }
    }

    public void updateNode(UUID uuid, @Nullable IGridNode node) {
        var entry = entries.get(uuid);
        if (entry != null) {
            entries.put(uuid, new TransmitterEntry(entry.dimension(), entry.pos(), node, entry.advanced()));
        }
    }

    public boolean isAdvanced(UUID uuid) {
        var entry = entries.get(uuid);
        return entry != null && entry.advanced();
    }

    // ── Listeners ──

    public void addListener(UUID uuid, TransmitterListener listener) {
        listeners.computeIfAbsent(uuid, k -> new ArrayList<>()).add(listener);
    }

    public void removeListener(UUID uuid, TransmitterListener listener) {
        var list = listeners.get(uuid);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(uuid);
            }
        }
    }

    private void fireListeners(UUID uuid, boolean available) {
        var list = listeners.get(uuid);
        if (list == null || list.isEmpty()) return;
        for (var listener : List.copyOf(list)) {
            listener.onTransmitterChanged(uuid, available);
        }
    }

    // ── Lookup ──

    @Nullable
    public TransmitterEntry find(UUID uuid) {
        return entries.get(uuid);
    }

    /**
     * Resolve a transmitter entry to a live GridNode.
     * Always re-evaluates {@link WirelessTransmitterNodeProvider#getWirelessGridNode()}
     * when the target chunk is loaded, so network topology changes (e.g. vanilla
     * controller added/removed) are reflected immediately.
     */
    @Nullable
    public IGridNode resolveNode(UUID uuid, MinecraftServer server) {
        var entry = entries.get(uuid);
        if (entry == null) return null;

        ServerLevel targetLevel = server.getLevel(entry.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(entry.pos())) {
            return entry.cachedNode();
        }

        var be = targetLevel.getBlockEntity(entry.pos());
        if (be instanceof WirelessTransmitterNodeProvider provider) {
            IGridNode node = provider.getWirelessGridNode();
            updateNode(uuid, node);
            return node;
        }
        return null;
    }

    /**
     * Marker interface for block entities that provide a GridNode for wireless linking.
     */
    public interface WirelessTransmitterNodeProvider {
        @Nullable IGridNode getWirelessGridNode();
        @Nullable UUID getTransmitterUUID();
    }
}
