package com.moakiee.ae2lt.me.cell;

import java.util.Arrays;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 * Array-indexed storage engine for the infinite cell.
 * <p>
 * Pure data store — no capacity checks. Capacity enforcement lives in
 * {@link InfiniteCellInventory} via {@link ByteTracker}.
 * <p>
 * Each {@link AEKey} is assigned a dense integer id on first insert.
 * Amounts are stored in parallel {@code long[]} arrays ({@code lo[id]},
 * {@code hi[id]}) for cache-friendly access. A per-key dirty bitset
 * ensures that {@link #persist} only re-serializes modified entries.
 * <p>
 * Per-{@link AEKeyType} aggregates (total amount hi/lo, key count) are
 * maintained incrementally so that {@link ByteTracker} can rebuild in
 * O(keyTypes) instead of O(totalKeys).
 */
final class IndexedStorage {

    private static final int INITIAL_CAPACITY = 256;

    // Key registry
    private final Object2IntOpenHashMap<AEKey> keyToId = new Object2IntOpenHashMap<>();
    private AEKey[] idToKey;
    private int nextId;
    private int[] freeIds;
    private int freeCount;

    // Amount arrays (63+63 bit)
    private long[] lo;
    private long[] hi;

    // Per-key dirty bitset + serialization cache
    private long[] dirtyBits;
    private CompoundTag[] tagCache;

    // Dense entry index for O(active) persist instead of O(nextId)
    private int[] idToEntryIndex;
    private int[] entryIndexToId;
    private int entryCount;
    private boolean structureDirty;

    private int totalTypes;
    private boolean needsPersist;
    private long modCount;

    // Per-AEKeyType aggregates — maintained incrementally
    private final Object2IntOpenHashMap<AEKeyType> typeCounts = new Object2IntOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKeyType> typeAmountLo = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKeyType> typeAmountHi = new Object2LongOpenHashMap<>();

    record TypeTotal(long hi, long lo) {}

    IndexedStorage() {
        keyToId.defaultReturnValue(-1);
        initArrays(INITIAL_CAPACITY);
    }

    int getTotalTypes() { return totalTypes; }

    boolean needsPersist() { return needsPersist; }

    long getModCount() { return modCount; }

    /**
     * Per-{@link AEKeyType} total amounts (126-bit hi/lo).
     * Maintained incrementally — O(keyTypes) to iterate.
     */
    Object2LongOpenHashMap<AEKeyType> getTypeAmountLo() { return typeAmountLo; }
    Object2LongOpenHashMap<AEKeyType> getTypeAmountHi() { return typeAmountHi; }

    /**
     * Per-{@link AEKeyType} unique key counts.
     * Maintained incrementally — O(keyTypes) to iterate.
     */
    Object2IntOpenHashMap<AEKeyType> getTypeCounts() { return typeCounts; }

    // ══════════════════════════════════════════════════════════════════════
    //  insert — raw, no capacity check
    // ══════════════════════════════════════════════════════════════════════

    long insert(AEKey key, long amount, Actionable mode) {
        if (amount <= 0) return 0;
        if (mode == Actionable.SIMULATE) return amount;

        int id = keyToId.getInt(key);
        boolean isNewKey = (id == -1);
        if (isNewKey) {
            id = allocateId(key);
            totalTypes++;
        }

        long newLo = lo[id] + amount;
        if (newLo < 0) { newLo &= Long.MAX_VALUE; hi[id]++; }
        lo[id] = newLo;
        dirtyBits[id >> 6] |= 1L << (id & 63);

        // update per-type aggregates
        AEKeyType kt = key.getType();
        if (isNewKey) typeCounts.addTo(kt, 1);
        long sumLo = typeAmountLo.getLong(kt) + amount;
        long sumHi = typeAmountHi.getLong(kt);
        if (sumLo < 0) { sumLo &= Long.MAX_VALUE; sumHi++; }
        typeAmountLo.put(kt, sumLo);
        typeAmountHi.put(kt, sumHi);

        needsPersist = true;
        modCount++;
        return amount;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  extract
    // ══════════════════════════════════════════════════════════════════════

    long extract(AEKey key, long amount, Actionable mode) {
        if (amount <= 0) return 0;

        int id = keyToId.getInt(key);
        if (id == -1) return 0;

        long curLo = lo[id], curHi = hi[id];
        long taken = DualLong126.geq(curHi, curLo, amount) ? amount : curLo;

        if (mode == Actionable.SIMULATE) return taken;

        long newLo = curLo - taken;
        if (newLo < 0) { newLo &= Long.MAX_VALUE; hi[id]--; }

        boolean keyRemoved = (newLo == 0 && hi[id] == 0);
        if (keyRemoved) {
            recycleId(id, key);
            totalTypes--;
        } else {
            lo[id] = newLo;
        }

        dirtyBits[id >> 6] |= 1L << (id & 63);

        // update per-type aggregates
        AEKeyType kt = key.getType();
        long sumLo = typeAmountLo.getLong(kt) - taken;
        long sumHi = typeAmountHi.getLong(kt);
        if (sumLo < 0) { sumLo &= Long.MAX_VALUE; sumHi--; }
        if (keyRemoved) {
            int remaining = typeCounts.addTo(kt, -1);
            if (remaining <= 0) {
                typeCounts.removeInt(kt);
                typeAmountLo.removeLong(kt);
                typeAmountHi.removeLong(kt);
            } else {
                typeAmountLo.put(kt, sumLo);
                typeAmountHi.put(kt, sumHi);
            }
        } else {
            typeAmountLo.put(kt, sumLo);
            typeAmountHi.put(kt, sumHi);
        }

        needsPersist = true;
        modCount++;
        return taken;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Queries
    // ══════════════════════════════════════════════════════════════════════

    void getAvailableStacks(KeyCounter out) {
        for (int id = 0; id < nextId; id++) {
            if (idToKey[id] != null) {
                out.add(idToKey[id], DualLong126.cap(hi[id], lo[id]));
            }
        }
    }

    boolean containsKey(AEKey key) {
        return keyToId.containsKey(key);
    }

    long getAmount(AEKey key) {
        int id = keyToId.getInt(key);
        if (id == -1) return 0;
        return DualLong126.cap(hi[id], lo[id]);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Persist — dirty keys only, incremental on lastRoot when possible
    // ══════════════════════════════════════════════════════════════════════

    CompoundTag persist(@Nullable CompoundTag lastRoot, HolderLookup.Provider registries) {
        return persist(lastRoot, (key, reg) -> key.toTagGeneric(reg), registries);
    }

    CompoundTag persist(@Nullable CompoundTag lastRoot, KeySerializer keySerializer, HolderLookup.Provider registries) {
        boolean canUpdateInPlace = lastRoot != null && !structureDirty;
        ListTag entries = canUpdateInPlace
                ? lastRoot.getList("entries", Tag.TAG_COMPOUND)
                : null;

        int words = (nextId + 63) >> 6;
        for (int w = 0; w < words; w++) {
            long bits = dirtyBits[w];
            if (bits == 0) continue;
            dirtyBits[w] = 0;
            while (bits != 0) {
                int bit = Long.numberOfTrailingZeros(bits);
                int id = (w << 6) | bit;
                bits &= bits - 1;

                if (id < nextId && idToKey[id] != null) {
                    CompoundTag tag = new CompoundTag();
                    tag.put("key", keySerializer.toTag(idToKey[id], registries));
                    tag.putLong("lo", lo[id]);
                    if (hi[id] != 0) tag.putLong("hi", hi[id]);
                    tagCache[id] = tag;
                    if (canUpdateInPlace) {
                        entries.set(idToEntryIndex[id], tag);
                    }
                } else {
                    if (id < tagCache.length) tagCache[id] = null;
                }
            }
        }

        CompoundTag root;
        if (canUpdateInPlace) {
            root = lastRoot;
        } else {
            entries = new ListTag();
            for (int i = 0; i < entryCount; i++) {
                entries.add(tagCache[entryIndexToId[i]]);
            }
            structureDirty = false;
            root = new CompoundTag();
            root.put("entries", entries);
        }

        root.putInt("totalTypes", totalTypes);
        needsPersist = false;
        return root;
    }

    @FunctionalInterface
    interface KeySerializer {
        CompoundTag toTag(AEKey key, HolderLookup.Provider registries);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Load — rebuild from flat NBT
    // ══════════════════════════════════════════════════════════════════════

    void load(CompoundTag root, HolderLookup.Provider registries) {
        keyToId.clear();
        nextId = 0;
        freeCount = 0;
        entryCount = 0;
        totalTypes = 0;
        structureDirty = false;
        typeCounts.clear();
        typeAmountLo.clear();
        typeAmountHi.clear();

        ListTag entries = root.getList("entries", Tag.TAG_COMPOUND);
        ensureCapacity(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
            if (key == null) continue;

            int id = allocateId(key);
            lo[id] = entry.getLong("lo");
            hi[id] = entry.contains("hi") ? entry.getLong("hi") : 0L;
            tagCache[id] = entry;
            totalTypes++;

            // rebuild per-type aggregates
            AEKeyType kt = key.getType();
            typeCounts.addTo(kt, 1);
            long sumLo = typeAmountLo.getLong(kt) + lo[id];
            long sumHi = typeAmountHi.getLong(kt) + hi[id];
            if (sumLo < 0) { sumLo &= Long.MAX_VALUE; sumHi++; }
            typeAmountLo.put(kt, sumLo);
            typeAmountHi.put(kt, sumHi);
        }

        Arrays.fill(dirtyBits, 0, (nextId + 63) >> 6, 0L);
        structureDirty = false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ID lifecycle
    // ══════════════════════════════════════════════════════════════════════

    private int allocateId(AEKey key) {
        int id;
        if (freeCount > 0) {
            id = freeIds[--freeCount];
        } else {
            id = nextId++;
            ensureCapacity(id);
        }
        keyToId.put(key, id);
        idToKey[id] = key;
        lo[id] = 0;
        hi[id] = 0;
        tagCache[id] = null;

        idToEntryIndex[id] = entryCount;
        if (entryCount >= entryIndexToId.length) {
            entryIndexToId = Arrays.copyOf(entryIndexToId, entryIndexToId.length * 2);
        }
        entryIndexToId[entryCount] = id;
        entryCount++;
        structureDirty = true;
        return id;
    }

    private void recycleId(int id, AEKey key) {
        keyToId.removeInt(key);
        idToKey[id] = null;
        lo[id] = 0;
        hi[id] = 0;
        tagCache[id] = null;

        int ei = idToEntryIndex[id];
        int lastEi = --entryCount;
        if (ei != lastEi) {
            int movedId = entryIndexToId[lastEi];
            entryIndexToId[ei] = movedId;
            idToEntryIndex[movedId] = ei;
        }
        idToEntryIndex[id] = -1;
        structureDirty = true;

        if (freeCount == freeIds.length) {
            freeIds = Arrays.copyOf(freeIds, freeIds.length * 2);
        }
        freeIds[freeCount++] = id;
    }

    private void ensureCapacity(int required) {
        if (required < lo.length) return;
        int oldLen = lo.length;
        int newCap = Math.max(INITIAL_CAPACITY, Integer.highestOneBit(required) << 1);
        lo = Arrays.copyOf(lo, newCap);
        hi = Arrays.copyOf(hi, newCap);
        idToKey = Arrays.copyOf(idToKey, newCap);
        tagCache = Arrays.copyOf(tagCache, newCap);
        idToEntryIndex = Arrays.copyOf(idToEntryIndex, newCap);
        Arrays.fill(idToEntryIndex, oldLen, newCap, -1);
        long[] newDirty = new long[(newCap + 63) >> 6];
        System.arraycopy(dirtyBits, 0, newDirty, 0, dirtyBits.length);
        dirtyBits = newDirty;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ══════════════════════════════════════════════════════════════════════

    private void initArrays(int capacity) {
        lo = new long[capacity];
        hi = new long[capacity];
        idToKey = new AEKey[capacity];
        tagCache = new CompoundTag[capacity];
        dirtyBits = new long[(capacity + 63) >> 6];
        idToEntryIndex = new int[capacity];
        Arrays.fill(idToEntryIndex, -1);
        entryIndexToId = new int[capacity];
        freeIds = new int[64];
    }
}
