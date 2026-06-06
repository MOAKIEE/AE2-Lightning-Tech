package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.GridConnection;
import org.jetbrains.annotations.Nullable;

public final class WirelessLinkOps {
    private WirelessLinkOps() {
    }

    public static boolean hasLiveConnection(@Nullable IGridConnection connection, @Nullable IGridNode node) {
        if (connection == null || node == null) {
            return false;
        }
        for (var candidate : node.getConnections()) {
            if (candidate == connection) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConnectedTo(@Nullable IGridConnection connection, @Nullable IGridNode node, @Nullable IGridNode other) {
        return hasLiveConnection(connection, node)
                && !connection.isInWorld()
                && other != null
                && connection.getOtherSide(node) == other;
    }

    public static void destroy(@Nullable IGridConnection connection, @Nullable IGridNode node) {
        if (hasLiveConnection(connection, node) && !connection.isInWorld()) {
            connection.destroy();
        }
    }

    public static IGridConnection createVirtualConnection(IGridNode targetNode, IGridNode transmitterNode) {
        for (var connection : targetNode.getConnections()) {
            if (connection.getOtherSide(targetNode) == transmitterNode) {
                if (!connection.isInWorld()) {
                    return connection;
                }
                throw new IllegalStateException("A physical connection already exists between the target and transmitter.");
            }
        }
        return GridConnection.create(targetNode, transmitterNode, null);
    }
}
