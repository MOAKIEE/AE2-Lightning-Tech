package com.moakiee.ae2lt.grid.wirelesslink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WirelessLinkIndex {
    private static final int MIN_TOMBSTONES_BEFORE_COMPACT = 128;

    private final Map<UUID, WirelessLink> byId = new LinkedHashMap<>();
    private final Map<TargetKey, LinkedHashMap<UUID, WirelessLink>> byTarget = new HashMap<>();
    private final Map<ExactKey, LinkedHashMap<UUID, WirelessLink>> byExactTarget = new HashMap<>();
    private final List<UUID> orderedIds = new ArrayList<>();

    private int cursor;
    private int tombstones;

    boolean isEmpty() {
        return byId.isEmpty();
    }

    boolean contains(UUID linkId) {
        return byId.containsKey(linkId);
    }

    Collection<WirelessLink> values() {
        return byId.values();
    }

    List<WirelessLink> findAllInDimension(String dimensionId) {
        var matches = new ArrayList<WirelessLink>();
        for (var link : byId.values()) {
            if (link.dimensionId().equals(dimensionId)) {
                matches.add(link);
            }
        }
        return matches;
    }

    void clear() {
        byId.clear();
        byTarget.clear();
        byExactTarget.clear();
        orderedIds.clear();
        cursor = 0;
        tombstones = 0;
    }

    void put(WirelessLink link) {
        var previous = byId.put(link.linkId(), link);
        if (previous == null) {
            orderedIds.add(link.linkId());
        } else {
            removeFromIndexes(previous);
        }
        addToIndexes(link);
    }

    WirelessLink remove(UUID linkId) {
        var removed = byId.remove(linkId);
        if (removed == null) {
            return null;
        }
        removeFromIndexes(removed);
        tombstones++;
        compactOrderIfNeeded();
        return removed;
    }

    WirelessLink find(int frequencyId, String dimensionId, long posLong, WirelessLinkMode mode, String sideName) {
        return firstValue(byExactTarget.get(new ExactKey(
                frequencyId,
                new TargetKey(dimensionId, posLong, mode, normalizeSide(sideName)))));
    }

    WirelessLink findAny(String dimensionId, long posLong, WirelessLinkMode mode, String sideName) {
        return firstValue(byTarget.get(new TargetKey(dimensionId, posLong, mode, normalizeSide(sideName))));
    }

    List<WirelessLink> nextBatch(int requestedBatchSize) {
        if (byId.isEmpty() || requestedBatchSize <= 0) {
            return List.of();
        }

        int limit = Math.min(requestedBatchSize, byId.size());
        var batch = new ArrayList<WirelessLink>(limit);
        int scanned = 0;
        int scanLimit = orderedIds.size();
        while (batch.size() < limit && scanned < scanLimit && !orderedIds.isEmpty()) {
            if (cursor >= orderedIds.size()) {
                cursor = 0;
            }
            var link = byId.get(orderedIds.get(cursor++));
            scanned++;
            if (link != null) {
                batch.add(link);
            }
        }
        return batch;
    }

    private void addToIndexes(WirelessLink link) {
        var target = targetKey(link);
        byTarget.computeIfAbsent(target, ignored -> new LinkedHashMap<>()).put(link.linkId(), link);
        byExactTarget
                .computeIfAbsent(new ExactKey(link.frequencyId(), target), ignored -> new LinkedHashMap<>())
                .put(link.linkId(), link);
    }

    private void removeFromIndexes(WirelessLink link) {
        removeFromBucket(byTarget, targetKey(link), link.linkId());
        removeFromBucket(byExactTarget, new ExactKey(link.frequencyId(), targetKey(link)), link.linkId());
    }

    private static <K> void removeFromBucket(Map<K, LinkedHashMap<UUID, WirelessLink>> buckets, K key, UUID linkId) {
        var bucket = buckets.get(key);
        if (bucket == null) {
            return;
        }
        bucket.remove(linkId);
        if (bucket.isEmpty()) {
            buckets.remove(key);
        }
    }

    private static WirelessLink firstValue(LinkedHashMap<UUID, WirelessLink> bucket) {
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        return bucket.values().iterator().next();
    }

    private static TargetKey targetKey(WirelessLink link) {
        return new TargetKey(link.dimensionId(), link.posLong(), link.mode(), normalizeSide(link.sideName()));
    }

    private static String normalizeSide(String sideName) {
        return sideName == null ? "" : sideName;
    }

    private void compactOrderIfNeeded() {
        if (tombstones < MIN_TOMBSTONES_BEFORE_COMPACT || tombstones * 4 < orderedIds.size()) {
            return;
        }
        orderedIds.clear();
        orderedIds.addAll(byId.keySet());
        if (cursor > orderedIds.size()) {
            cursor = orderedIds.size();
        }
        tombstones = 0;
    }

    private record TargetKey(String dimensionId, long posLong, WirelessLinkMode mode, String sideName) {
    }

    private record ExactKey(int frequencyId, TargetKey target) {
    }
}
