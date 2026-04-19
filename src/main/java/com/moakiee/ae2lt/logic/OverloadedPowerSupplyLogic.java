package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;

import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.logic.energy.BufferedMEStorage;
import com.moakiee.ae2lt.logic.energy.BufferedStorageService;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyAPI;

/**
 * Wireless FE distribution logic for the Overloaded Power Supply.
 *
 * <p>Gating by Flux Cell presence:
 * <ul>
 * <li><b>No cell installed</b>: runs in plain NORMAL AppFlux-forwarding mode
 *     — one {@code send()} per target per activation, no cost multiplier,
 *     no caching, no bursts. Mirrors the main-branch Pattern Provider's
 *     simple induction-card forwarding. OVERLOAD mode (set via GUI) is
 *     ignored in this state.</li>
 * <li><b>Cell installed</b>: unlocks the full feature set. NORMAL mode still
 *     performs one send per target but routes through the cell-backed
 *     cache (reducing ME read pressure). OVERLOAD mode uses a lightweight
 *     sentinel+ticket scheduler and {@code sendMulti(..., 64)} bursts with
 *     a 2× FE cost multiplier.</li>
 * </ul>
 */
public class OverloadedPowerSupplyLogic implements IGridTickable {

    public enum Status {
        IDLE,
        APPFLUX_UNAVAILABLE,
        NO_CELL,
        NO_GRID,
        NO_CONNECTIONS,
        NO_VALID_TARGETS,
        NO_NETWORK_FE,
        TARGET_UNSUPPORTED,
        TARGET_BLOCKED,
        ACTIVE
    }

    private static final int TICK_MIN = 1;
    private static final int TICK_MAX = 20;
    private static final int OVERLOAD_MAX_CONNECTIONS = 64;
    private static final int OVERLOAD_MAX_CALLS = 64;
    private static final int TICKET_DURATION = 20;
    private static final int SENTINEL_BUCKETS = 5;

    private final OverloadedPowerSupplyBlockEntity host;
    private final IActionSource actionSource;
    private final Map<WirelessEnergyAPI.Target, Long> tickets = new HashMap<>();

    @Nullable
    private IStorageService delegateStorageService;
    @Nullable
    private BufferedMEStorage bufferedStorage;
    @Nullable
    private BufferedStorageService storageProxy;

    private int sentinelIndex;
    private Status lastStatus = Status.NO_CELL;
    private long lastTransferAmount;

    public OverloadedPowerSupplyLogic(OverloadedPowerSupplyBlockEntity host) {
        this.host = host;
        this.actionSource = IActionSource.ofMachine(host);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_MIN, TICK_MAX, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!(host.getLevel() instanceof ServerLevel serverLevel)) {
            return TickRateModulation.SLEEP;
        }

        boolean didWork = tick(serverLevel);
        if (isOverloadActive() && getActiveTicketCount() > 0) {
            return TickRateModulation.URGENT;
        }

        if (host.getConnections().isEmpty()) {
            return TickRateModulation.IDLE;
        }

        return didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    /**
     * Overload mode is only active when both the player has selected it AND
     * a Flux Cell is installed to back the cache. Without a cell we strip
     * the feature down to plain NORMAL forwarding.
     */
    private boolean isOverloadActive() {
        return host.getMode() == OverloadedPowerSupplyBlockEntity.PowerMode.OVERLOAD
                && host.getBufferCapacity() > 0L;
    }

    public void onStateChanged() {
        if (host.getBufferCapacity() <= 0L && bufferedStorage != null) {
            flushBufferToNetwork();
        }
        if (!isOverloadActive()) {
            tickets.clear();
        }
        sentinelIndex = 0;
        if (!AppFluxBridge.canUseEnergyHandler()) {
            setStatus(Status.APPFLUX_UNAVAILABLE);
        } else if (host.getConnections().isEmpty()) {
            setStatus(Status.NO_CONNECTIONS);
        } else {
            setStatus(Status.IDLE);
        }
        host.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    /**
     * Flush any cached FE back to the ME network and zero the buffer. Safe to
     * call on any lifecycle boundary: cell removed, grid disconnected, BE
     * removed / unloaded. FE the network refuses is dropped (there is no
     * persistent storage on this device by design).
     */
    public void flushBufferToNetwork() {
        var feKey = AppFluxBridge.FE_KEY;
        if (bufferedStorage == null || feKey == null) {
            if (bufferedStorage != null) {
                bufferedStorage.clearBuffer();
            }
            return;
        }
        bufferedStorage.flushAll(feKey, actionSource);
        bufferedStorage.clearBuffer();
    }

    public long getBufferedEnergy() {
        return bufferedStorage != null ? bufferedStorage.getBufferedEnergy() : 0L;
    }

    public int getActiveTicketCount() {
        if (!(host.getLevel() instanceof ServerLevel serverLevel)) {
            return tickets.size();
        }

        long gameTime = serverLevel.getGameTime();
        int count = 0;
        for (long expiry : tickets.values()) {
            if (expiry >= gameTime) {
                count++;
            }
        }
        return count;
    }

    public Status getLastStatus() {
        return lastStatus;
    }

    public long getLastTransferAmount() {
        return lastTransferAmount;
    }

    private boolean tick(ServerLevel serverLevel) {
        if (!AppFluxBridge.canUseEnergyHandler()) {
            tickets.clear();
            flushBufferToNetwork();
            setStatus(Status.APPFLUX_UNAVAILABLE);
            return false;
        }

        var mainNode = host.getMainNode();
        if (!mainNode.isActive() || mainNode.getGrid() == null) {
            setStatus(Status.NO_GRID);
            return false;
        }

        BufferedStorageService proxy = ensureStorageProxy();
        BufferedMEStorage buffer = bufferedStorage;
        if (proxy == null || buffer == null) {
            setStatus(Status.NO_GRID);
            return false;
        }

        boolean overloadActive = isOverloadActive();
        buffer.advanceHistory();
        // Capacity drives the buffering mode: >0 cell-backed or RAM cache,
        // 0 pass-through (plain ME → AppFlux forward).
        buffer.setBufferCapacity(host.getBufferCapacity());
        buffer.setCostMultiplier(overloadActive ? 2 : 1);

        long gameTime = serverLevel.getGameTime();
        if (gameTime % 20L == 0L) {
            host.clearInvalidConnections();
        }

        List<WirelessEnergyAPI.Target> validTargets = getValidTargets(serverLevel);
        if (validTargets.isEmpty()) {
            tickets.clear();
            setStatus(host.getConnections().isEmpty() ? Status.NO_CONNECTIONS : Status.NO_VALID_TARGETS);
            return false;
        }

        setStatus(Status.IDLE);
        boolean didWork = overloadActive
                ? tickOverload(serverLevel, validTargets)
                : tickNormal(serverLevel, validTargets);

        return didWork;
    }

    private boolean tickNormal(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        tickets.clear();

        boolean didWork = false;
        for (WirelessEnergyAPI.Target target : validTargets) {
            didWork |= push(serverLevel, target, 1) > 0L;
        }
        updateIdleFailureStatus(didWork);
        return didWork;
    }

    private boolean tickOverload(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        long gameTime = serverLevel.getGameTime();
        if (validTargets.size() > OVERLOAD_MAX_CONNECTIONS) {
            validTargets = new ArrayList<>(validTargets.subList(0, OVERLOAD_MAX_CONNECTIONS));
        }

        pruneTickets(validTargets, gameTime);

        boolean didWork = false;
        var idleTargets = new ArrayList<WirelessEnergyAPI.Target>();
        for (WirelessEnergyAPI.Target target : validTargets) {
            if (!tickets.containsKey(target)) {
                idleTargets.add(target);
            }
        }

        if (!idleTargets.isEmpty()) {
            int scans = Math.max(1, (idleTargets.size() + SENTINEL_BUCKETS - 1) / SENTINEL_BUCKETS);
            int baseIndex = idleTargets.isEmpty() ? 0 : sentinelIndex % idleTargets.size();
            for (int i = 0; i < scans; i++) {
                WirelessEnergyAPI.Target target = idleTargets.get((baseIndex + i) % idleTargets.size());
                long pushed = push(serverLevel, target, 1);
                if (pushed > 0L) {
                    tickets.put(target, gameTime + TICKET_DURATION);
                    didWork = true;
                }
            }
            sentinelIndex = (baseIndex + scans) % idleTargets.size();
        } else {
            sentinelIndex = 0;
        }

        for (WirelessEnergyAPI.Target target : validTargets) {
            Long expiry = tickets.get(target);
            if (expiry == null || expiry < gameTime) {
                continue;
            }

            long pushed = push(serverLevel, target, OVERLOAD_MAX_CALLS);
            if (pushed > 0L) {
                tickets.put(target, gameTime + TICKET_DURATION);
                didWork = true;
            }
        }

        pruneTickets(validTargets, gameTime);
        updateIdleFailureStatus(didWork);
        return didWork;
    }

    private long push(ServerLevel providerLevel, WirelessEnergyAPI.Target target, int maxCalls) {
        BufferedStorageService proxy = storageProxy;
        if (proxy == null) {
            return 0L;
        }

        Object capCache = WirelessEnergyAPI.resolveCapCache(providerLevel, target, () -> host.getMainNode().getGrid());
        if (capCache == null) {
            setStatus(Status.NO_VALID_TARGETS);
            return 0L;
        }

        long pushed = maxCalls <= 1
                ? WirelessEnergyAPI.send(capCache, target.face(), proxy, actionSource)
                : WirelessEnergyAPI.sendMulti(capCache, target.face(), proxy, actionSource, maxCalls);
        if (pushed > 0L) {
            setActive(pushed);
            return pushed;
        }
        if (pushed < 0L) {
            setStatus(Status.TARGET_UNSUPPORTED);
        }
        return 0L;
    }

    private List<WirelessEnergyAPI.Target> getValidTargets(ServerLevel serverLevel) {
        var result = new ArrayList<WirelessEnergyAPI.Target>(host.getConnections().size());
        for (var conn : host.getConnections()) {
            var target = new WirelessEnergyAPI.Target(conn.dimension(), conn.pos(), conn.boundFace());
            if (WirelessEnergyAPI.resolveLevel(serverLevel.getServer(), target) != null) {
                result.add(target);
            }
        }
        return result;
    }

    private void pruneTickets(List<WirelessEnergyAPI.Target> validTargets, long gameTime) {
        tickets.entrySet().removeIf(entry -> entry.getValue() < gameTime || !validTargets.contains(entry.getKey()));
    }

    @Nullable
    private BufferedStorageService ensureStorageProxy() {
        var grid = host.getMainNode().getGrid();
        if (grid == null) {
            // Grid gone: flush the existing cache into whichever delegate it
            // is still bound to before dropping the references, otherwise the
            // FE extracted from the old network is silently deleted.
            flushBufferToNetwork();
            delegateStorageService = null;
            bufferedStorage = null;
            storageProxy = null;
            return null;
        }

        IStorageService storageService = grid.getStorageService();
        if (storageProxy == null || bufferedStorage == null || delegateStorageService != storageService) {
            if (bufferedStorage != null) {
                flushBufferToNetwork();
            }
            bufferedStorage = new BufferedMEStorage(storageService.getInventory(), host::getInstalledCellStorage);
            storageProxy = new BufferedStorageService(storageService, bufferedStorage);
            delegateStorageService = storageService;
        }

        return storageProxy;
    }

    private void updateIdleFailureStatus(boolean didWork) {
        if (didWork || lastStatus != Status.IDLE) {
            return;
        }
        setStatus(hasAvailableNetworkFe() ? Status.TARGET_BLOCKED : Status.NO_NETWORK_FE);
    }

    private boolean hasAvailableNetworkFe() {
        var feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || delegateStorageService == null) {
            return false;
        }
        return delegateStorageService.getInventory().extract(feKey, 1L, Actionable.SIMULATE, actionSource) > 0L;
    }

    private void setStatus(Status status) {
        lastStatus = status;
        if (status != Status.ACTIVE) {
            lastTransferAmount = 0L;
        }
    }

    private void setActive(long amount) {
        lastStatus = Status.ACTIVE;
        lastTransferAmount = amount;
    }
}
