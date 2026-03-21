package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;

/**
 * Global static registry mapping interception positions ({@code M.relative(F)})
 * to eject-mode handler entries.
 * <p>
 * Three-level Map: {@code dim -> pos.asLong() -> EnumMap<Direction, EjectEntry>}
 * <ul>
 *     <li>Outer dim map provides a fast-path for dimensions with no eject registrations.</li>
 *     <li>Inner EnumMap supports 3D stacking: multiple machines may register different
 *         faces at the same adjacent position.</li>
 * </ul>
 */
public final class EjectModeRegistry {

    public record EjectEntry(
            WeakReference<OverloadedPatternProviderBlockEntity> providerRef,
            GhostOutputBlockEntity ghostBE
    ) {}

    private static final Map<ResourceKey<Level>, Map<Long, EnumMap<Direction, List<EjectEntry>>>>
            registrations = new HashMap<>();

    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    public static void setBypass(boolean value) {
        BYPASS.set(value);
    }

    public static boolean isBypassed() {
        return BYPASS.get();
    }

    private EjectModeRegistry() {}

    public static void register(ResourceKey<Level> dim, long posLong, Direction face, EjectEntry entry) {
        registrations
                .computeIfAbsent(dim, k -> new HashMap<>())
                .computeIfAbsent(posLong, k -> new EnumMap<>(Direction.class))
                .computeIfAbsent(face, k -> new ArrayList<>())
                .add(entry);
    }

    public static void unregister(ResourceKey<Level> dim, long posLong, Direction face) {
        var dimMap = registrations.get(dim);
        if (dimMap == null) return;
        var faceMap = dimMap.get(posLong);
        if (faceMap == null) return;
        faceMap.remove(face);
        if (faceMap.isEmpty()) {
            dimMap.remove(posLong);
            if (dimMap.isEmpty()) {
                registrations.remove(dim);
            }
        }
    }

    /**
     * Look up an entry by (dim, pos, face). Used by the BlockCapability Mixin.
     * Returns the first entry whose provider reference is still alive.
     */
    @Nullable
    public static EjectEntry lookupByFace(ResourceKey<Level> dim, long posLong, Direction face) {
        var dimMap = registrations.get(dim);
        if (dimMap == null) return null;
        var faceMap = dimMap.get(posLong);
        if (faceMap == null) return null;
        var list = faceMap.get(face);
        if (list == null) return null;
        for (var entry : list) {
            if (entry.providerRef().get() != null) return entry;
        }
        return null;
    }

    /**
     * Look up any entry at (dim, pos), regardless of face.
     * Used by the getBlockEntity Mixin (which has no face context).
     * Returns the first registered entry with a live provider, or null.
     */
    @Nullable
    public static EjectEntry lookupAny(ResourceKey<Level> dim, long posLong) {
        var dimMap = registrations.get(dim);
        if (dimMap == null) return null;
        var faceMap = dimMap.get(posLong);
        if (faceMap == null) return null;
        for (var list : faceMap.values()) {
            for (var entry : list) {
                if (entry.providerRef().get() != null) return entry;
            }
        }
        return null;
    }

    public record DimPos(ResourceKey<Level> dimension, BlockPos pos) {}

    /**
     * Unregister all entries whose provider reference matches the given BE.
     *
     * @return positions that had entries removed, for capability cache invalidation
     */
    public static List<DimPos> unregisterAll(OverloadedPatternProviderBlockEntity provider) {
        var removed = new ArrayList<DimPos>();
        for (var dimIt = registrations.entrySet().iterator(); dimIt.hasNext(); ) {
            var dimEntry = dimIt.next();
            var dim = dimEntry.getKey();
            var dimMap = dimEntry.getValue();
            for (var posIt = dimMap.entrySet().iterator(); posIt.hasNext(); ) {
                var posEntry = posIt.next();
                var faceMap = posEntry.getValue();
                boolean any = false;
                for (var faceIt = faceMap.entrySet().iterator(); faceIt.hasNext(); ) {
                    var faceEntry = faceIt.next();
                    var list = faceEntry.getValue();
                    boolean changed = list.removeIf(e -> {
                        var ref = e.providerRef().get();
                        return ref == null || ref == provider;
                    });
                    if (changed) any = true;
                    if (list.isEmpty()) faceIt.remove();
                }
                if (any) {
                    removed.add(new DimPos(dim, BlockPos.of(posEntry.getKey())));
                }
                if (faceMap.isEmpty()) posIt.remove();
            }
            if (dimMap.isEmpty()) dimIt.remove();
        }
        return removed;
    }
}
