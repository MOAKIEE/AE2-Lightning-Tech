package com.moakiee.ae2lt.machine.wireless;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Manages a single wireless connection between two {@link WirelessNode}s.
 * <p>
 * Each connector registers itself under a <em>positive</em> frequency key;
 * its partner is looked up under the <em>negated</em> frequency.
 * This prevents a node from connecting to itself.
 */
public class WirelessConnect implements IActionHost {

    // ── Static registry ────────────────────────────────────────────────

    private static final Map<Long, WirelessConnect> REGISTRY = new HashMap<>();

    @Nullable
    public static WirelessConnect lookup(Level level, long key) {
        if (level.isClientSide()) {
            return null;
        }
        return REGISTRY.get(key);
    }

    private static void register(long key, WirelessConnect connect) {
        REGISTRY.put(key, connect);
    }

    private static void unregister(long key) {
        REGISTRY.remove(key);
    }

    // ── Instance state ─────────────────────────────────────────────────

    private WirelessNode host;
    private long thisSide;
    private long otherSide;
    @Nullable
    private IGridConnection connection;
    private double distance;
    private boolean shutdown = true;
    private boolean registered;
    private boolean destroyed;

    public WirelessConnect(WirelessNode host) {
        this.host = host;
    }

    // ── Public API ─────────────────────────────────────────────────────

    public boolean isConnected() {
        return !shutdown && connection != null;
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public double getDistance() {
        return distance;
    }

    @Nullable
    public WirelessNode getRemote() {
        var remote = lookup(host.getLevel(), otherSide);
        if (remote != null && !remote.destroyed) {
            return remote.host;
        }
        return null;
    }

    public WirelessStatus getStatus() {
        if (host.getFrequency() == 0) {
            return WirelessStatus.UNCONNECTED;
        }
        if (shutdown || connection == null) {
            return WirelessStatus.REMOTE_ERROR;
        }
        IGridNode node = host.getGridNode();
        if (node != null && !node.isPowered()) {
            return WirelessStatus.NO_POWER;
        }
        return WirelessStatus.WORKING;
    }

    /**
     * Called every server tick to maintain the wireless connection.
     */
    public void updateStatus() {
        if (destroyed) {
            return;
        }

        long freq = host.getFrequency();

        // Re-register if frequency changed.
        if (freq != Math.abs(thisSide)) {
            destroyConnection();
            if (thisSide != 0) {
                unregister(thisSide);
            }
            if (freq == 0) {
                thisSide = 0;
                otherSide = 0;
                shutdown = true;
                return;
            }
            // Decide polarity: if -freq slot is free, we take +freq; otherwise -freq.
            if (canClaimSlot(freq)) {
                thisSide = freq;
                otherSide = -freq;
            } else {
                thisSide = -freq;
                otherSide = freq;
            }
            register(thisSide, this);
        }

        if (!registered) {
            NeoForge.EVENT_BUS.register(this);
            registered = true;
        }

        if (thisSide == 0) {
            shutdown = true;
            return;
        }

        // Look up partner.
        WirelessConnect remote = lookup(host.getLevel(), otherSide);
        if (remote == null || remote.destroyed || remote.host == null) {
            destroyConnection();
            shutdown = true;
            return;
        }

        // Same block entity? Ignore.
        if (remote.host.getBlockEntity() == host.getBlockEntity()
                && remote.host.getBlockPos().equals(host.getBlockPos())) {
            destroyConnection();
            shutdown = true;
            return;
        }

        // Range check.
        Level remoteLevel = remote.host.getLevel();
        if (remoteLevel == null || remoteLevel != host.getLevel()) {
            destroyConnection();
            shutdown = true;
            return;
        }

        BlockPos remotePos = remote.host.getBlockPos();
        distance = Math.sqrt(host.getBlockPos().distSqr(remotePos));
        if (distance > AE2LTCommonConfig.wirelessConnectorMaxRange()) {
            destroyConnection();
            shutdown = true;
            return;
        }

        // Ensure grid connection exists and points at the correct nodes.
        IGridNode nodeA = host.getGridNode();
        IGridNode nodeB = remote.host.getGridNode();
        if (nodeA == null || nodeB == null) {
            destroyConnection();
            shutdown = true;
            return;
        }

        // Check if we already own a valid connection.
        if (connection != null) {
            if ((connection.a() == nodeA && connection.b() == nodeB)
                    || (connection.a() == nodeB && connection.b() == nodeA)) {
                shutdown = false;
                return;
            }
            // Nodes changed — rebuild.
            destroyConnection();
        }

        // Check if the partner already created a connection between our nodes
        // (e.g. partner ran updateStatus first and connected to us).
        for (var existing : nodeA.getConnections()) {
            if (existing.getOtherSide(nodeA) == nodeB) {
                connection = existing;
                shutdown = false;
                return;
            }
        }

        // Create a new grid connection.
        try {
            connection = GridHelper.createConnection(nodeA, nodeB);
            shutdown = false;
        } catch (Exception e) {
            // Already connected (race) — try to find the existing connection.
            for (var existing : nodeA.getConnections()) {
                if (existing.getOtherSide(nodeA) == nodeB) {
                    connection = existing;
                    shutdown = false;
                    return;
                }
            }
            shutdown = true;
        }
    }

    /**
     * Permanently tears down this wireless link. Call when the block is
     * removed or the chunk unloads.
     */
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        destroyConnection();
        if (thisSide != 0) {
            unregister(thisSide);
            thisSide = 0;
        }
        if (registered) {
            NeoForge.EVENT_BUS.unregister(this);
            registered = false;
        }
        host = null;
    }

    // ── IActionHost ────────────────────────────────────────────────────

    @Override
    @Nullable
    public IGridNode getActionableNode() {
        return host != null ? host.getGridNode() : null;
    }

    // ── Event handling ─────────────────────────────────────────────────

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (host != null && host.getLevel() == event.getLevel()) {
            destroy();
        }
    }

    // ── Internal ───────────────────────────────────────────────────────

    private void destroyConnection() {
        if (connection != null) {
            try {
                connection.destroy();
            } catch (Exception ignored) {
            }
            connection = null;
        }
    }

    private boolean canClaimSlot(long freq) {
        WirelessConnect existing = REGISTRY.get(freq);
        if (existing == null || existing.destroyed) {
            return true;
        }
        // Slot taken by a different node.
        return false;
    }
}
