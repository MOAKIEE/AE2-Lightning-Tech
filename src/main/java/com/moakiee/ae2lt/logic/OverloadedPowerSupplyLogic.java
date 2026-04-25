package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

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
 * <li><b>No cell installed</b>: NORMAL runs as plain AppFlux-forwarding
 *     — scheduled one-call sends, no cost multiplier, no caching, no bursts.
 *     OVERLOAD mode refuses to run and reports {@link Status#NO_CELL}.</li>
 * <li><b>Cell installed</b>: unlocks OVERLOAD mode. NORMAL mode remains a
 *     no-disk one-ME-extract batch; OVERLOAD keeps the cell contents staged
 *     in a long cache and only refills from ME when that cache reaches zero.</li>
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
    private static final int ENERGY_DELAY_MEAN = 5;
    private static final int ENERGY_DELAY_MAX = 20;
    private static final int ENERGY_DELAY_MIN = 1;
    private static final int ENERGY_WHEEL_SLOTS = ENERGY_DELAY_MAX;
    private static final int ENERGY_WHEEL_INITIAL_CAPACITY = 32;

    private static final class EnergyScheduleState {
        int scheduleDelay = ENERGY_DELAY_MEAN;
    }

    private record ScheduleEntry(WirelessEnergyAPI.Target target, EnergyScheduleState state) {
    }

    private final OverloadedPowerSupplyBlockEntity host;
    private final IActionSource actionSource;
    private final Map<WirelessEnergyAPI.Target, Long> tickets = new HashMap<>();
    private final Map<WirelessEnergyAPI.Target, Object> capCaches = new HashMap<>();
    private final Map<WirelessEnergyAPI.Target, Object> energyTargets = new HashMap<>();
    private final Map<WirelessEnergyAPI.Target, EnergyScheduleState> normalScheduleStates = new HashMap<>();

    @SuppressWarnings("unchecked")
    private final ArrayList<ScheduleEntry>[] normalEnergyWheel = new ArrayList[ENERGY_WHEEL_SLOTS];
    {
        for (int i = 0; i < ENERGY_WHEEL_SLOTS; i++) {
            normalEnergyWheel[i] = new ArrayList<>(ENERGY_WHEEL_INITIAL_CAPACITY);
        }
    }
    private final ArrayList<ScheduleEntry> normalBatch = new ArrayList<>(ENERGY_WHEEL_INITIAL_CAPACITY);
    private ArrayList<ScheduleEntry> spareNormalWheelList = new ArrayList<>(ENERGY_WHEEL_INITIAL_CAPACITY);
    private List<WirelessEnergyAPI.Target> normalWheelTargets = List.of();
    private List<OverloadedPowerSupplyBlockEntity.WirelessConnection> cachedConnections = List.of();
    private List<WirelessEnergyAPI.Target> cachedConnectionTargets = List.of();
    private final List<WirelessEnergyAPI.Target> cachedValidTargets = new ArrayList<>();
    private final Set<WirelessEnergyAPI.Target> cachedValidTargetSet = new HashSet<>();
    private long validTargetsCacheTick = Long.MIN_VALUE;
    private long[] normalBatchDemand = new long[0];

    @Nullable
    private IStorageService delegateStorageService;
    @Nullable
    private BufferedMEStorage bufferedStorage;
    @Nullable
    private BufferedStorageService storageProxy;

    private int sentinelIndex;
    private int normalWheelPointer;
    private boolean normalWheelDirty = true;
    private Status lastStatus = Status.IDLE;
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

        if (host.getMode() == OverloadedPowerSupplyBlockEntity.PowerMode.NORMAL) {
            return TickRateModulation.URGENT;
        }

        return didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    /**
     * Overload mode is only active when both the player has selected it AND
     * a Flux Cell is installed to back the cache.
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
        cachedConnections = List.of();
        cachedConnectionTargets = List.of();
        cachedValidTargets.clear();
        cachedValidTargetSet.clear();
        validTargetsCacheTick = Long.MIN_VALUE;
        resetNormalWheel();
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

    public void persistCellCache() {
        var feKey = AppFluxBridge.FE_KEY;
        if (bufferedStorage != null && feKey != null) {
            bufferedStorage.flushAll(feKey, actionSource);
        }
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
            capCaches.clear();
            energyTargets.clear();
            resetNormalWheel();
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

        boolean wantsOverload = host.getMode() == OverloadedPowerSupplyBlockEntity.PowerMode.OVERLOAD;
        boolean hasCell = host.getBufferCapacity() > 0L;
        boolean overloadActive = wantsOverload && hasCell;

        if (wantsOverload && !hasCell) {
            tickets.clear();
            capCaches.clear();
            energyTargets.clear();
            resetNormalWheel();
            setStatus(Status.NO_CELL);
            return false;
        }

        buffer.advanceHistory();
        buffer.setCostMultiplier(overloadActive ? 2 : 1);

        long gameTime = serverLevel.getGameTime();
        if (gameTime % 20L == 0L) {
            host.clearInvalidConnections();
        }

        List<WirelessEnergyAPI.Target> validTargets = getValidTargets(serverLevel);
        if (validTargets.isEmpty()) {
            if (overloadActive) {
                flushBufferToNetwork();
            }
            tickets.clear();
            capCaches.clear();
            energyTargets.clear();
            resetNormalWheel();
            setStatus(host.getConnections().isEmpty() ? Status.NO_CONNECTIONS : Status.NO_VALID_TARGETS);
            return false;
        }
        capCaches.keySet().retainAll(cachedValidTargetSet);
        energyTargets.keySet().retainAll(cachedValidTargetSet);

        setStatus(Status.IDLE);
        lastTransferAmount = 0L;
        boolean didWork = overloadActive
                ? tickOverload(serverLevel, validTargets)
                : tickNormal(serverLevel, validTargets);

        return didWork;
    }

    private boolean tickNormal(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        tickets.clear();
        if (normalWheelDirty || !validTargets.equals(normalWheelTargets)) {
            rebuildNormalWheel(validTargets);
        }

        normalWheelPointer = (normalWheelPointer + 1) % ENERGY_WHEEL_SLOTS;
        normalBatch.clear();
        var dueEntries = pollNormalWheel();
        normalBatch.addAll(dueEntries);
        dueEntries.clear();
        if (normalBatch.isEmpty()) {
            return false;
        }

        var feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || bufferedStorage == null || storageProxy == null) {
            for (ScheduleEntry entry : normalBatch) {
                scheduleNormalEntry(entry);
            }
            normalBatch.clear();
            return false;
        }

        ensureNormalDemandCapacity(normalBatch.size());
        long totalDemand = 0L;
        long perCallLimit = transferLimitPerCall();
        for (int i = 0; i < normalBatch.size(); i++) {
            var target = normalBatch.get(i).target();
            Object energyTarget = getEnergyTarget(serverLevel, target);
            long demand = energyTarget != null
                    ? Math.min(WirelessEnergyAPI.simulateTarget(energyTarget, perCallLimit), perCallLimit)
                    : 0L;
            normalBatchDemand[i] = demand;
            totalDemand = saturatingAdd(totalDemand, demand);
        }

        long pulled = totalDemand > 0L ? bufferedStorage.beginMemoryBatch(feKey, totalDemand, actionSource) : 0L;
        boolean didWork = false;
        try {
            for (int i = 0; i < normalBatch.size(); i++) {
                ScheduleEntry entry = normalBatch.get(i);
                long demand = normalBatchDemand[i];
                if (demand <= 0L) {
                    adjustNormalDelay(entry, 0L);
                    scheduleNormalEntry(entry);
                    continue;
                }
                long pushed = pushKnownDemand(serverLevel, entry.target(), demand);
                didWork |= pushed > 0L;
                adjustNormalDelay(entry, pushed);
                scheduleNormalEntry(entry);
            }
        } finally {
            if (totalDemand > 0L) {
                bufferedStorage.endBatch(feKey, actionSource);
            }
            normalBatch.clear();
        }
        updateIdleFailureStatus(didWork, pulled > 0L);
        return didWork;
    }

    private boolean tickOverload(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        var feKey = AppFluxBridge.FE_KEY;
        boolean stagedCell = feKey != null
                && bufferedStorage != null
                && bufferedStorage.beginCellBatch(feKey, actionSource);
        try {
            return tickOverloadStaged(serverLevel, validTargets);
        } finally {
            if (stagedCell) {
                bufferedStorage.endCellBatch(feKey, actionSource, serverLevel.getGameTime() % 20L == 0L);
            }
        }
    }

    private boolean tickOverloadStaged(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        long gameTime = serverLevel.getGameTime();
        if (validTargets.size() > OVERLOAD_MAX_CONNECTIONS) {
            validTargets = new ArrayList<>(validTargets.subList(0, OVERLOAD_MAX_CONNECTIONS));
        }

        pruneTickets(gameTime);

        boolean didWork = false;
        int idleCount = 0;
        for (WirelessEnergyAPI.Target target : validTargets) {
            if (!tickets.containsKey(target)) {
                idleCount++;
            }
        }

        int scans = idleCount == 0
                ? 0
                : Math.max(1, (idleCount + SENTINEL_BUCKETS - 1) / SENTINEL_BUCKETS);
        if (idleCount > 0) {
            int size = validTargets.size();
            int index = sentinelIndex % size;
            int visited = 0;
            int found = 0;
            while (visited < size && found < scans) {
                WirelessEnergyAPI.Target target = validTargets.get(index);
                index = (index + 1) % size;
                visited++;
                if (tickets.containsKey(target)) {
                    continue;
                }
                found++;
                long pushed = push(serverLevel, target, 1);
                if (pushed > 0L) {
                    tickets.put(target, gameTime + TICKET_DURATION);
                    didWork = true;
                }
            }
            sentinelIndex = index;
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

        pruneTickets(gameTime);
        updateIdleFailureStatus(didWork, true);
        return didWork;
    }

    private long push(ServerLevel providerLevel, WirelessEnergyAPI.Target target, int maxCalls) {
        return push(providerLevel, target, maxCalls, transferLimitPerCall());
    }

    private long push(ServerLevel providerLevel, WirelessEnergyAPI.Target target, int maxCalls, long maxFe) {
        BufferedMEStorage buffer = bufferedStorage;
        if (buffer == null) {
            return 0L;
        }

        Object energyTarget = getEnergyTarget(providerLevel, target);
        if (energyTarget == null) {
            setStatus(Status.NO_VALID_TARGETS);
            return 0L;
        }

        long pushed = maxCalls <= 1
                ? WirelessEnergyAPI.sendToTargetOptimistic(energyTarget, buffer, actionSource, maxFe)
                : sendToTargetMulti(energyTarget, buffer, maxCalls, maxFe);
        if (pushed > 0L) {
            setActive(pushed);
            return pushed;
        }
        if (pushed < 0L) {
            setStatus(Status.TARGET_UNSUPPORTED);
        }
        return 0L;
    }

    private long pushKnownDemand(ServerLevel providerLevel, WirelessEnergyAPI.Target target, long demand) {
        BufferedStorageService proxy = storageProxy;
        if (proxy == null || demand <= 0L) {
            return 0L;
        }

        Object energyTarget = getEnergyTarget(providerLevel, target);
        if (energyTarget == null) {
            setStatus(Status.NO_VALID_TARGETS);
            return 0L;
        }

        long pushed = WirelessEnergyAPI.sendToTargetKnownDemand(energyTarget, proxy, actionSource, demand);
        if (pushed > 0L) {
            setActive(pushed);
            return pushed;
        }
        return 0L;
    }

    private long sendToTargetMulti(Object energyTarget, BufferedMEStorage buffer, int maxCalls, long maxFe) {
        long total = 0L;
        for (int i = 0; i < maxCalls; i++) {
            long pushed = WirelessEnergyAPI.sendToTargetOptimistic(energyTarget, buffer, actionSource, maxFe);
            if (pushed <= 0L) {
                break;
            }
            total = saturatingAdd(total, pushed);
        }
        return total;
    }

    @Nullable
    private Object getCapCache(ServerLevel providerLevel, WirelessEnergyAPI.Target target) {
        Object capCache = capCaches.get(target);
        if (capCache == null) {
            capCache = WirelessEnergyAPI.resolveCapCache(providerLevel, target, () -> host.getMainNode().getGrid());
            if (capCache != null) {
                capCaches.put(target, capCache);
            }
        }
        return capCache;
    }

    @Nullable
    private Object getEnergyTarget(ServerLevel providerLevel, WirelessEnergyAPI.Target target) {
        Object energyTarget = energyTargets.get(target);
        if (energyTarget == null) {
            Object capCache = getCapCache(providerLevel, target);
            energyTarget = WirelessEnergyAPI.resolveEnergyTarget(capCache, target.face());
            if (energyTarget != null) {
                energyTargets.put(target, energyTarget);
            }
        }
        return energyTarget;
    }

    private List<WirelessEnergyAPI.Target> getValidTargets(ServerLevel serverLevel) {
        var connections = host.getConnections();
        if (!connections.equals(cachedConnections)) {
            var rebuilt = new ArrayList<WirelessEnergyAPI.Target>(connections.size());
            for (var conn : connections) {
                rebuilt.add(new WirelessEnergyAPI.Target(conn.dimension(), conn.pos(), conn.boundFace()));
            }
            cachedConnections = List.copyOf(connections);
            cachedConnectionTargets = List.copyOf(rebuilt);
            cachedValidTargets.clear();
            cachedValidTargetSet.clear();
            validTargetsCacheTick = Long.MIN_VALUE;
        }

        long gameTime = serverLevel.getGameTime();
        if (validTargetsCacheTick == Long.MIN_VALUE || gameTime % 20L == 0L) {
            cachedValidTargets.clear();
            cachedValidTargetSet.clear();
            for (var target : cachedConnectionTargets) {
                if (WirelessEnergyAPI.resolveLevel(serverLevel.getServer(), target) != null) {
                    cachedValidTargets.add(target);
                    cachedValidTargetSet.add(target);
                }
            }
            validTargetsCacheTick = gameTime;
        }

        return cachedValidTargets;
    }

    private void pruneTickets(long gameTime) {
        tickets.entrySet().removeIf(entry -> entry.getValue() < gameTime || !cachedValidTargetSet.contains(entry.getKey()));
    }

    private List<ScheduleEntry> pollNormalWheel() {
        var list = normalEnergyWheel[normalWheelPointer];
        if (list.isEmpty()) {
            return list;
        }
        normalEnergyWheel[normalWheelPointer] = spareNormalWheelList;
        spareNormalWheelList = list;
        return list;
    }

    private void scheduleNormalEntry(ScheduleEntry entry) {
        int slot = (normalWheelPointer + entry.state().scheduleDelay) % ENERGY_WHEEL_SLOTS;
        normalEnergyWheel[slot].add(entry);
    }

    private void rebuildNormalWheel(List<WirelessEnergyAPI.Target> validTargets) {
        for (var slot : normalEnergyWheel) {
            slot.clear();
            slot.ensureCapacity(ENERGY_WHEEL_INITIAL_CAPACITY);
        }
        capCaches.keySet().retainAll(cachedValidTargetSet);
        energyTargets.keySet().retainAll(cachedValidTargetSet);
        spareNormalWheelList.clear();
        spareNormalWheelList.ensureCapacity(ENERGY_WHEEL_INITIAL_CAPACITY);
        normalBatch.clear();
        normalBatch.ensureCapacity(ENERGY_WHEEL_INITIAL_CAPACITY);
        normalScheduleStates.keySet().retainAll(cachedValidTargetSet);
        normalWheelTargets = List.copyOf(validTargets);
        normalWheelPointer = 0;
        for (var target : validTargets) {
            var state = normalScheduleStates.computeIfAbsent(target, ignored -> new EnergyScheduleState());
            scheduleNormalEntry(new ScheduleEntry(target, state));
        }
        normalWheelDirty = false;
    }

    private void resetNormalWheel() {
        for (var slot : normalEnergyWheel) {
            slot.clear();
        }
        normalBatch.clear();
        normalBatchDemand = new long[0];
        spareNormalWheelList.clear();
        normalWheelTargets = List.of();
        normalWheelPointer = 0;
        normalWheelDirty = true;
    }

    private void ensureNormalDemandCapacity(int size) {
        if (normalBatchDemand.length < size) {
            normalBatchDemand = new long[size];
        }
    }

    private long transferLimitPerCall() {
        if (AppFluxBridge.TRANSFER_RATE <= 0L) {
            return 0L;
        }
        return AppFluxBridge.TRANSFER_RATE;
    }

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }

    private void adjustNormalDelay(ScheduleEntry entry, long pushed) {
        var state = entry.state();
        int delay = state.scheduleDelay;
        if (pushed > 0L) {
            delay = delay > ENERGY_DELAY_MEAN ? delay / 2 : delay - 1;
        } else {
            delay++;
        }
        state.scheduleDelay = Math.clamp(delay, ENERGY_DELAY_MIN, ENERGY_DELAY_MAX);
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
            bufferedStorage = new BufferedMEStorage(
                    storageService.getInventory(),
                    host::getInstalledCellStorage,
                    host::persistCellStorage);
            storageProxy = new BufferedStorageService(storageService, bufferedStorage);
            delegateStorageService = storageService;
        }

        return storageProxy;
    }

    private void updateIdleFailureStatus(boolean didWork, boolean hadEnergyBudget) {
        if (didWork || lastStatus != Status.IDLE) {
            return;
        }
        setStatus(hadEnergyBudget ? Status.TARGET_BLOCKED : Status.NO_NETWORK_FE);
    }

    private void setStatus(Status status) {
        lastStatus = status;
        if (status != Status.ACTIVE) {
            lastTransferAmount = 0L;
        }
    }

    private void setActive(long amount) {
        lastStatus = Status.ACTIVE;
        lastTransferAmount += amount;
    }
}
