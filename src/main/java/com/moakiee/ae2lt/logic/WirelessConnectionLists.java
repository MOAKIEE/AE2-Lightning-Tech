package com.moakiee.ae2lt.logic;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class WirelessConnectionLists {
    public record PruneResult(int removed, int nextCursor) {
    }

    private WirelessConnectionLists() {
    }

    public static boolean isLocalDimension(@Nullable Level level, ResourceKey<Level> dimension) {
        return level == null || level.dimension().equals(dimension);
    }

    public static <T extends WirelessConnectionRef> int indexOf(
            List<T> source,
            ResourceKey<Level> dimension,
            BlockPos pos) {
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).sameTarget(dimension, pos)) {
                return i;
            }
        }
        return -1;
    }

    public static <T extends WirelessConnectionRef> boolean addOrReplace(
            List<T> source,
            T connection,
            int maxConnections) {
        int index = indexOf(source, connection.dimension(), connection.pos());
        if (index >= 0) {
            source.set(index, connection);
            return true;
        }
        if (source.size() >= maxConnections) {
            return false;
        }
        source.add(connection);
        return true;
    }

    public static <T extends WirelessConnectionRef> void writeValueList(
            ValueOutput data,
            String tagName,
            List<T> connections) {
        var list = data.childrenList(tagName);
        for (var connection : connections) {
            connection.writeTo(list.addChild());
        }
    }

    public static <T extends WirelessConnectionRef> void readValueList(
            ValueInput data,
            String tagName,
            List<T> target,
            int maxConnections,
            Function<ValueInput, T> reader) {
        target.clear();
        for (var entry : data.childrenListOrEmpty(tagName)) {
            if (target.size() >= maxConnections) {
                break;
            }
            target.add(reader.apply(entry));
        }
    }

    public static <T extends WirelessConnectionRef> PruneResult pruneInvalid(
            List<T> connections,
            int cursor,
            int maxChecks,
            ServerLevel hostLevel,
            BlockPos hostPos) {
        return pruneInvalidInternal(connections, cursor, maxChecks, hostLevel, hostPos, null);
    }

    public static <T extends WirelessConnectionRef> PruneResult pruneInvalid(
            List<T> connections,
            int cursor,
            int maxChecks,
            ServerLevel hostLevel,
            BlockPos hostPos,
            Predicate<T> removalGuard) {
        return pruneInvalidInternal(connections, cursor, maxChecks, hostLevel, hostPos, removalGuard);
    }

    private static <T extends WirelessConnectionRef> PruneResult pruneInvalidInternal(
            List<T> connections,
            int cursor,
            int maxChecks,
            ServerLevel hostLevel,
            BlockPos hostPos,
            @Nullable Predicate<T> removalGuard) {
        if (connections.isEmpty()) {
            return new PruneResult(0, 0);
        }
        if (maxChecks <= 0) {
            return new PruneResult(0, Math.min(Math.max(cursor, 0), connections.size() - 1));
        }

        int checksRemaining = Math.min(maxChecks, connections.size());
        int removed = 0;
        int index = Math.min(Math.max(cursor, 0), connections.size() - 1);

        while (checksRemaining-- > 0 && !connections.isEmpty()) {
            if (index >= connections.size()) {
                index = 0;
            }

            var connection = connections.get(index);
            if (WirelessConnectionValidator.validate(hostLevel, hostPos, connection)
                    == WirelessConnectionValidator.Status.REMOVE
                    && (removalGuard == null || removalGuard.test(connection))) {
                connections.remove(index);
                removed++;
            } else {
                index++;
            }
        }

        return new PruneResult(removed, connections.isEmpty() ? 0 : index % connections.size());
    }
}
