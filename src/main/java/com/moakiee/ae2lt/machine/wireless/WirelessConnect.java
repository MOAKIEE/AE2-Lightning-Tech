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
 * Uses positive/negative frequency pairing to prevent self-connection.
 */
public class WirelessConnect implements IActionHost {

    private static final Map<Long, WirelessConnect> REGISTRY = new HashMap<>();

    @Nullable
    static WirelessConnect lookup(Level level, long key) {
        if (level.isClientSide()) {
            return null;
        }
        return REGISTRY.get(key);
    }

    public static void clearRegistry() {
        REGISTRY.clear();
    }

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

    public boolean isConnected() {
        return !shutdown && connection != null;
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

    public void updateStatus() {
        if (destroyed) {
            return;
        }

        long freq = host.getFrequency();

        if (freq != Math.abs(thisSide)) {
            destroyConnection();
            if (thisSide != 0) {
                REGISTRY.remove(thisSide);
            }
            if (freq == 0) {
                thisSide = 0;
                otherSide = 0;
                shutdown = true;
                return;
            }
            if (canClaimSlot(freq)) {
                thisSide = freq;
                otherSide = -freq;
            } else {
                thisSide = -freq;
                otherSide = freq;
            }
            REGISTRY.put(thisSide, this);
        }

        if (!registered) {
            NeoForge.EVENT_BUS.register(this);
            registered = true;
        }

        if (thisSide == 0) {
            shutdown = true;
            return;
        }

        WirelessConnect remote = lookup(host.getLevel(), otherSide);
        if (remote == null || remote.destroyed || remote.host == null) {
            destroyConnection();
            shutdown = true;
            return;
        }

        if (remote.host.getBlockEntity() == host.getBlockEntity()
                && remote.host.getBlockPos().equals(host.getBlockPos())) {
            destroyConnection();
            shutdown = true;
            return;
        }

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

        IGridNode nodeA = host.getGridNode();
        IGridNode nodeB = remote.host.getGridNode();
        if (nodeA == null || nodeB == null) {
            destroyConnection();
            shutdown = true;
            return;
        }

        if (connection != null) {
            if ((connection.a() == nodeA && connection.b() == nodeB)
                    || (connection.a() == nodeB && connection.b() == nodeA)) {
                shutdown = false;
                return;
            }
            destroyConnection();
        }

        // Adopt existing connection created by partner.
        IGridConnection found = findExistingConnection(nodeA, nodeB);
        if (found != null) {
            connection = found;
            shutdown = false;
            return;
        }

        try {
            connection = GridHelper.createConnection(nodeA, nodeB);
            shutdown = false;
        } catch (Exception e) {
            found = findExistingConnection(nodeA, nodeB);
            if (found != null) {
                connection = found;
                shutdown = false;
            } else {
                shutdown = true;
            }
        }
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        destroyConnection();
        if (thisSide != 0) {
            REGISTRY.remove(thisSide);
            thisSide = 0;
        }
        if (registered) {
            NeoForge.EVENT_BUS.unregister(this);
            registered = false;
        }
        host = null;
    }

    @Override
    @Nullable
    public IGridNode getActionableNode() {
        return host != null ? host.getGridNode() : null;
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (host != null && host.getLevel() == event.getLevel()) {
            destroy();
        }
    }

    public static double calculatePowerForConnection(double distance, double discount, double multiplier) {
        double dis = Math.max(distance, Math.E);
        return Math.max(1.0, dis * Math.log(dis) * discount) * multiplier;
    }

    private void destroyConnection() {
        if (connection != null) {
            try {
                connection.destroy();
            } catch (Exception ignored) {
            }
            connection = null;
        }
    }

    @Nullable
    private static IGridConnection findExistingConnection(IGridNode a, IGridNode b) {
        for (var conn : a.getConnections()) {
            if (conn.getOtherSide(a) == b) {
                return conn;
            }
        }
        return null;
    }

    private boolean canClaimSlot(long freq) {
        WirelessConnect existing = REGISTRY.get(freq);
        return existing == null || existing.destroyed;
    }
}
