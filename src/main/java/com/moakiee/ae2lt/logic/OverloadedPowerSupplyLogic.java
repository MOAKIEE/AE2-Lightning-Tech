package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;

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
import com.moakiee.ae2lt.logic.energy.TargetAccess;
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
    /**
     * Wheel size rounded up to the next power of two so {@code (pointer +
     * delay) & ENERGY_WHEEL_SLOTS_MASK} replaces the {@code % 20} division
     * that showed up under tickNormal in profiling. The 12 extra slots
     * beyond {@link #ENERGY_DELAY_MAX} stay empty during steady state — wasted
     * memory is ~1 KiB total, paid once.
     */
    private static final int ENERGY_WHEEL_SLOTS = 32;
    private static final int ENERGY_WHEEL_SLOTS_MASK = ENERGY_WHEEL_SLOTS - 1;
    private static final int ENERGY_WHEEL_INITIAL_CAPACITY = 32;

    /**
     * Inline-int wheel slot.
     *
     * <p>Replaces the previous {@code ArrayList<ScheduleEntry>} per slot. Stores
     * raw int target indices (into {@link #cachedValidTargets} /
     * {@link #connectionTargetCaches}), eliminating per-tick record accessor
     * indirection and per-rebuild {@code ScheduleEntry} allocations.
     */
    private static final class IntWheelSlot {
        int[] data = new int[ENERGY_WHEEL_INITIAL_CAPACITY];
        int size;

        void add(int i) {
            if (size >= data.length) {
                data = Arrays.copyOf(data, data.length << 1);
            }
            data[size++] = i;
        }

        void clear() {
            size = 0;
        }
    }

    private final OverloadedPowerSupplyBlockEntity host;
    private final IActionSource actionSource;
    private final Map<WirelessEnergyAPI.Target, BlockEnergyTargetCache> targetCachePool = new HashMap<>();

    private final IntWheelSlot[] normalEnergyWheel = new IntWheelSlot[ENERGY_WHEEL_SLOTS];
    {
        for (int i = 0; i < ENERGY_WHEEL_SLOTS; i++) {
            normalEnergyWheel[i] = new IntWheelSlot();
        }
    }
    private IntWheelSlot spareNormalWheelSlot = new IntWheelSlot();
    private List<OverloadedPowerSupplyBlockEntity.WirelessConnection> cachedConnections = List.of();
    private List<WirelessEnergyAPI.Target> cachedConnectionTargets = List.of();
    private final List<WirelessEnergyAPI.Target> cachedValidTargets = new ArrayList<>();
    private final Set<WirelessEnergyAPI.Target> cachedValidTargetSet = new HashSet<>();
    private final ArrayList<WirelessEnergyAPI.Target> nextValidTargets = new ArrayList<>();
    private final HashSet<WirelessEnergyAPI.Target> nextValidTargetSet = new HashSet<>();
    private final ArrayList<OverloadedPowerSupplyBlockEntity.WirelessConnection> invalidConnections = new ArrayList<>();
    private long validTargetsCacheTick = Long.MIN_VALUE;
    private int validTargetsVersion;
    /**
     * Period (ticks) between full re-validation of valid target connections.
     * Connection invalidation is *primarily* delivered by NeoForge's capability
     * invalidation listeners ({@link BlockEnergyTargetCache#onInvalidate}); this
     * periodic sweep is purely a defensive net for chunk-load/dimension events
     * that aren't surfaced as cap invalidations. 100 ticks (5 s) keeps the
     * worst-case detection latency low without dragging the hot path down with
     * a 1-second BE/level lookup spike. The {@link #revalidationOffset} stagger
     * spreads multiple BE instances across that 5 s window so they don't all
     * spike the same tick.
     */
    private static final int REVALIDATION_INTERVAL = 100;
    private final int revalidationOffset = (System.identityHashCode(this) & 0x7FFFFFFF) % REVALIDATION_INTERVAL;
    private int normalWheelTargetsVersion = -1;
    /**
     * Per-connection ticket expiry, index-aligned with {@link #cachedValidTargets}.
     * Replaces the old {@code Map<Target, Long> tickets} HashMap so the hot path
     * (~64 connections per tick in OVERLOAD) uses array indexing instead of
     * {@code containsKey}/{@code put}/{@code remove}. Reset whenever the valid
     * target set version changes (in {@link #rebuildConnectionState}).
     */
    private long[] connectionTicketExpiry = new long[0];
    /**
     * Per-connection {@link BlockEnergyTargetCache} snapshot, index-aligned with
     * {@link #cachedValidTargets}. Cache instances are owned by
     * {@link #targetCachePool}; the array gives the hot path O(1) access without
     * a {@code computeIfAbsent} call per target per tick.
     */
    private BlockEnergyTargetCache[] connectionTargetCaches = new BlockEnergyTargetCache[0];
    private long[] normalBatchDemand = new long[0];
    /**
     * Resolved energy target reference per due slot, cached during the simulate
     * pass so the modulate pass does not have to call
     * {@link BlockEnergyTargetCache#resolve(ServerLevel)} a second time. Saves
     * one cap-cache lookup per active target per tick.
     */
    private TargetAccess[] normalBatchEnergyTargets = new TargetAccess[0];
    private int cachedConnectionVersion = -1;
    private WirelessEnergyAPI.Target[] overloadBatchTargets = new WirelessEnergyAPI.Target[OVERLOAD_MAX_CONNECTIONS];
    private TargetAccess[] overloadBatchEnergyTargets = new TargetAccess[OVERLOAD_MAX_CONNECTIONS];
    private boolean[] overloadBatchTicketed = new boolean[OVERLOAD_MAX_CONNECTIONS];
    private int overloadBatchSize;

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
        // Defensively persist the cell on every state-change boundary
        // (cell removed, mode toggled, connections edited, grid detached).
        // The cell IS the buffer now, so we can never carry stale FE forward
        // — but we still want the ItemStack data component to reflect the
        // latest in-memory storedEnergy before the cell potentially leaves
        // this BE.
        if (bufferedStorage != null) {
            flushBufferToNetwork();
        }
        if (!isOverloadActive() && connectionTicketExpiry.length > 0) {
            Arrays.fill(connectionTicketExpiry, 0L);
        }
        cachedConnections = List.of();
        cachedConnectionTargets = List.of();
        cachedValidTargets.clear();
        cachedValidTargetSet.clear();
        validTargetsCacheTick = Long.MIN_VALUE;
        cachedConnectionVersion = -1;
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
        long[] expiry = connectionTicketExpiry;
        int n = Math.min(expiry.length, cachedValidTargets.size());
        if (n == 0) {
            return 0;
        }

        if (!(host.getLevel() instanceof ServerLevel serverLevel)) {
            int count = 0;
            for (int i = 0; i < n; i++) {
                if (expiry[i] > 0L) {
                    count++;
                }
            }
            return count;
        }

        long gameTime = serverLevel.getGameTime();
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (expiry[i] >= gameTime) {
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
            enterIdleState(Status.APPFLUX_UNAVAILABLE, true);
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
            enterIdleState(Status.NO_CELL, false);
            return false;
        }

        buffer.advanceHistory();
        buffer.setCostMultiplier(overloadActive ? 2 : 1);

        List<WirelessEnergyAPI.Target> validTargets = getValidTargets(serverLevel);
        if (validTargets.isEmpty()) {
            enterIdleState(host.getConnections().isEmpty() ? Status.NO_CONNECTIONS : Status.NO_VALID_TARGETS,
                    overloadActive);
            return false;
        }
        setStatus(Status.IDLE);
        lastTransferAmount = 0L;
        boolean didWork;
        try {
            didWork = overloadActive
                    ? tickOverload(serverLevel, validTargets)
                    : tickNormal(serverLevel, validTargets);
        } finally {
            // Single per-tick persist gate: collapses up to 64*64 cell.saveChanges
            // callbacks (OVERLOAD) into one ItemStack write. Cheap when the cell
            // is clean (FluxCellInventory.persist is isPersisted-flag guarded).
            var feKey = AppFluxBridge.FE_KEY;
            if (feKey != null) {
                buffer.endTick(feKey, actionSource);
            }
        }

        return didWork;
    }

    /**
     * Drop all per-tick state and (optionally) flush the FE buffer back to ME.
     * Used by every {@link #tick} short-circuit so that lifecycle transitions
     * never leak tickets, target caches, or schedule-wheel entries.
     */
    private void enterIdleState(Status status, boolean flushBuffer) {
        if (connectionTicketExpiry.length > 0) {
            Arrays.fill(connectionTicketExpiry, 0L);
        }
        clearTargetCaches();
        resetNormalWheel();
        if (flushBuffer) {
            flushBufferToNetwork();
        }
        setStatus(status);
    }

    private boolean tickNormal(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        if (normalWheelDirty || normalWheelTargetsVersion != validTargetsVersion) {
            rebuildNormalWheel();
        }

        normalWheelPointer = (normalWheelPointer + 1) & ENERGY_WHEEL_SLOTS_MASK;
        IntWheelSlot due = pollNormalWheel();
        int dueSize = due.size;
        if (dueSize == 0) {
            return false;
        }
        int[] dueData = due.data;
        BlockEnergyTargetCache[] caches = connectionTargetCaches;

        var feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || bufferedStorage == null || storageProxy == null) {
            for (int i = 0; i < dueSize; i++) {
                scheduleNormalEntry(dueData[i]);
            }
            due.size = 0;
            return false;
        }

        ensureNormalBatchCapacity(dueSize);
        long[] demands = normalBatchDemand;
        TargetAccess[] resolvedTargets = normalBatchEnergyTargets;
        long totalDemand = 0L;
        // Inlined transferLimitPerCall — Math.max collapses the null/<=0
        // guard into a single CMOV.
        long perCallLimit = Math.max(0L, AppFluxBridge.TRANSFER_RATE);
        for (int i = 0; i < dueSize; i++) {
            // Hoist caches[dueData[i]] into a local so JIT only emits one
            // array bounds-check per iteration instead of two.
            BlockEnergyTargetCache cache = caches[dueData[i]];
            TargetAccess energyTarget = cache.resolve(serverLevel);
            resolvedTargets[i] = energyTarget;
            long demand = 0L;
            if (energyTarget != null) {
                // Skip WirelessEnergyAPI's identity-forward layer.
                long sim = AppFluxBridge.simulateTarget(energyTarget, perCallLimit);
                demand = sim > perCallLimit ? perCallLimit : sim;
            }
            demands[i] = demand;
            // Plain += is safe: demand <= perCallLimit <= TRANSFER_RATE, and
            // dueSize <= cachedValidTargets.size() <= OVERLOAD_MAX_CONNECTIONS
            // (64). 64 * TRANSFER_RATE never reaches Long.MAX_VALUE for any
            // realistic configured rate, so saturatingAdd's xor/and dance is
            // pure overhead in this loop.
            totalDemand += demand;
        }

        if (totalDemand <= 0L) {
            // Nothing to do: every due target is full or unreachable. Just
            // re-schedule via adjustAndScheduleNormalEntry — same call we'd
            // make in the modulate-pass demand<=0 branch — and return.
            for (int i = 0; i < dueSize; i++) {
                adjustAndScheduleNormalEntry(dueData[i], 0L);
                resolvedTargets[i] = null;
            }
            due.size = 0;
            updateIdleFailureStatus(false, false);
            return false;
        }

        long pulled = bufferedStorage.beginMemoryBatch(feKey, totalDemand, actionSource);
        if (pulled <= 0L) {
            try {
                requeueNormalEntriesSoon(due, 0);
                updateIdleFailureStatus(false, false);
                return false;
            } finally {
                bufferedStorage.endBatch(feKey, actionSource);
                Arrays.fill(resolvedTargets, 0, dueSize, null);
                due.size = 0;
            }
        }

        boolean didWork = false;
        long remainingBudget = pulled;
        // Hoist bufferedStorage / actionSource so the per-iteration call site
        // only does one field load instead of three. Pass `buffer` directly
        // to AppFluxBridge so it skips the IStorageService.getInventory()
        // interface dispatch — that's a ~5 ns saving per modulate-pass
        // iteration that compounds at N=64.
        BufferedMEStorage buffer = bufferedStorage;
        IActionSource src = actionSource;
        try {
            for (int i = 0; i < dueSize; i++) {
                int targetIndex = dueData[i];
                long demand = demands[i];
                if (demand <= 0L) {
                    adjustAndScheduleNormalEntry(targetIndex, 0L);
                    continue;
                }
                if (remainingBudget <= 0L) {
                    requeueNormalEntriesSoon(due, i);
                    break;
                }

                long requested = demand < remainingBudget ? demand : remainingBudget;
                // Inline of pushKnownDemand: drops two redundant null/<=0
                // guards (proxy was already null-checked at tickNormal entry,
                // resolvedTargets[i] is guaranteed non-null because the
                // simulate pass only set demand>0 when target!=null, and
                // demand>0 is the loop guard above). Saves one method dispatch
                // and three branches per due target.
                long pushed = AppFluxBridge.sendToTargetKnownDemand(
                        resolvedTargets[i], buffer, src, requested);
                if (pushed > 0L) {
                    lastStatus = Status.ACTIVE;
                    lastTransferAmount += pushed;
                    didWork = true;
                    remainingBudget -= pushed;
                }
                adjustAndScheduleNormalEntry(targetIndex, pushed);
            }
        } finally {
            buffer.endBatch(feKey, src);
            // Single Arrays.fill replaces a per-element null loop — faster
            // (intrinsified to memset on most archs) and clearer.
            Arrays.fill(resolvedTargets, 0, dueSize, null);
            due.size = 0;
        }
        // updateIdleFailureStatus(didWork, true) inlined: hadEnergyBudget=true
        // here (we already returned early on pulled<=0), so the only branch
        // that matters is "didWork && lastStatus==IDLE → TARGET_BLOCKED".
        // didWork=true means we already wrote ACTIVE above, nothing to do.
        if (!didWork && lastStatus == Status.IDLE) {
            setStatus(Status.TARGET_BLOCKED);
        }
        return didWork;
    }

    private boolean tickOverload(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        // No more begin/endCellBatch — the cell is the buffer. Each direct send
        // pulls from cell.storedEnergy via BufferedMEStorage.extractForDirectSend,
        // and inline-refills from the ME network when the cell runs short.
        // Persist of the ItemStack is centralised in tick(): a single endTick
        // call collapses every cell mutation in this tick into one persist.
        return tickOverloadStaged(serverLevel, validTargets);
    }

    private boolean tickOverloadStaged(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        long gameTime = serverLevel.getGameTime();
        try {
            int targetCount = prepareOverloadTargets(serverLevel, validTargets);
            if (targetCount == 0) {
                setStatus(Status.NO_VALID_TARGETS);
                return false;
            }

            long[] expiry = connectionTicketExpiry;
            boolean[] ticketed = overloadBatchTicketed;
            TargetAccess[] energyTargets = overloadBatchEnergyTargets;

            int idleCount = 0;
            for (int i = 0; i < targetCount; i++) {
                TargetAccess energyTarget = energyTargets[i];
                if (energyTarget == null) {
                    ticketed[i] = false;
                    continue;
                }
                boolean hasTicket = expiry[i] >= gameTime;
                ticketed[i] = hasTicket;
                if (!hasTicket) {
                    idleCount++;
                }
            }

            boolean didWork = false;
            int scans = idleCount == 0
                    ? 0
                    : Math.max(1, (idleCount + SENTINEL_BUCKETS - 1) / SENTINEL_BUCKETS);
            if (idleCount > 0) {
                int index = sentinelIndex % targetCount;
                int visited = 0;
                int found = 0;
                while (visited < targetCount && found < scans) {
                    int targetIndex = index;
                    index = (index + 1) % targetCount;
                    visited++;
                    TargetAccess energyTarget = energyTargets[targetIndex];
                    if (ticketed[targetIndex] || energyTarget == null) {
                        continue;
                    }
                    found++;
                    long pushed = pushPrepared(energyTarget, 1);
                    if (pushed > 0L) {
                        expiry[targetIndex] = gameTime + TICKET_DURATION;
                        ticketed[targetIndex] = true;
                        didWork = true;
                    }
                }
                sentinelIndex = index;
            } else {
                sentinelIndex = 0;
            }

            for (int i = 0; i < targetCount; i++) {
                if (!ticketed[i]) {
                    continue;
                }
                long pushed = pushPrepared(energyTargets[i], OVERLOAD_MAX_CALLS);
                if (pushed > 0L) {
                    expiry[i] = gameTime + TICKET_DURATION;
                    didWork = true;
                }
            }

            updateIdleFailureStatus(didWork, true);
            return didWork;
        } finally {
            clearOverloadBatch();
        }
    }

    private int prepareOverloadTargets(ServerLevel serverLevel, List<WirelessEnergyAPI.Target> validTargets) {
        int targetCount = Math.min(validTargets.size(), OVERLOAD_MAX_CONNECTIONS);
        ensureOverloadBatchCapacity(targetCount);
        BlockEnergyTargetCache[] caches = connectionTargetCaches;
        long[] expiry = connectionTicketExpiry;
        WirelessEnergyAPI.Target[] batchTargets = overloadBatchTargets;
        TargetAccess[] batchEnergyTargets = overloadBatchEnergyTargets;
        for (int i = 0; i < targetCount; i++) {
            WirelessEnergyAPI.Target target = validTargets.get(i);
            BlockEnergyTargetCache cache = caches[i];
            TargetAccess energyTarget = cache != null ? cache.resolve(serverLevel) : null;
            batchTargets[i] = target;
            batchEnergyTargets[i] = energyTarget;
            if (energyTarget == null && i < expiry.length) {
                expiry[i] = 0L;
            }
        }
        overloadBatchSize = targetCount;
        return targetCount;
    }

    private void ensureOverloadBatchCapacity(int size) {
        if (overloadBatchTargets.length < size) {
            overloadBatchTargets = new WirelessEnergyAPI.Target[size];
        }
        if (overloadBatchEnergyTargets.length < size) {
            overloadBatchEnergyTargets = new TargetAccess[size];
        }
        if (overloadBatchTicketed.length < size) {
            overloadBatchTicketed = new boolean[size];
        }
    }

    private void clearOverloadBatch() {
        for (int i = 0; i < overloadBatchSize; i++) {
            overloadBatchTargets[i] = null;
            overloadBatchEnergyTargets[i] = null;
            overloadBatchTicketed[i] = false;
        }
        overloadBatchSize = 0;
    }

    private long pushPrepared(TargetAccess energyTarget, int maxCalls) {
        BufferedMEStorage buffer = bufferedStorage;
        if (buffer == null) {
            return 0L;
        }

        long pushed = WirelessEnergyAPI.sendToTargetRepeatedOptimistic(
                energyTarget, buffer, actionSource, Math.max(0L, AppFluxBridge.TRANSFER_RATE), maxCalls);
        if (pushed > 0L) {
            setActive(pushed);
            return pushed;
        }
        if (pushed < 0L) {
            setStatus(Status.TARGET_UNSUPPORTED);
        }
        return 0L;
    }

    /**
     * Per-target lazy cache of the resolved AppFlux energy handle plus a
     * {@link ICapabilityInvalidationListener} hook. The listener is registered
     * directly with the target {@link ServerLevel} (one registration per
     * dimension this target has lived in) and is held only by NeoForge's
     * weak-reference listener list, so when this cache is dropped from
     * {@link #targetCachePool} it gets GC'd and silently unregisters itself.
     */
    private final class BlockEnergyTargetCache implements ICapabilityInvalidationListener {
        private final WirelessEnergyAPI.Target target;
        /**
         * Per-target NORMAL-mode schedule delay. Lives on the cache (not in a
         * separate {@code HashMap<Target, EnergyScheduleState>}) so it survives
         * across {@link #rebuildConnectionState} as long as the same target is
         * still connected — the cache instance is keyed by Target inside
         * {@link #targetCachePool}. New targets default to MEAN; removed
         * targets get their cache GC'd along with this delay.
         */
        int scheduleDelay = ENERGY_DELAY_MEAN;
        @Nullable
        private BlockEntity blockEntity;
        @Nullable
        private TargetAccess energyTarget;
        @Nullable
        private ServerLevel registeredLevel;

        private BlockEnergyTargetCache(WirelessEnergyAPI.Target target) {
            this.target = target;
        }

        @Override
        public boolean onInvalidate() {
            if (host.isRemoved()) {
                blockEntity = null;
                energyTarget = null;
                registeredLevel = null;
                return false;
            }
            blockEntity = null;
            energyTarget = null;
            return true;
        }

        @Nullable
        private TargetAccess resolve(ServerLevel providerLevel) {
            if (energyTarget != null && blockEntity != null && !blockEntity.isRemoved()) {
                return energyTarget;
            }

            ServerLevel targetLevel = WirelessEnergyAPI.resolveLevel(providerLevel.getServer(), target);
            if (targetLevel == null) {
                blockEntity = null;
                energyTarget = null;
                return null;
            }

            BlockEntity currentBlockEntity = targetLevel.getBlockEntity(target.pos());
            if (currentBlockEntity == null) {
                ensureRegistered(targetLevel);
                blockEntity = null;
                energyTarget = null;
                return null;
            }

            ensureRegistered(targetLevel);
            blockEntity = currentBlockEntity;

            Object capCache = WirelessEnergyAPI.resolveCapCache(providerLevel, target, () -> host.getMainNode().getGrid());
            if (capCache == null) {
                energyTarget = null;
                return null;
            }

            TargetAccess resolved = WirelessEnergyAPI.resolveEnergyTarget(capCache, target.face());
            energyTarget = resolved;
            return resolved;
        }

        private void ensureRegistered(ServerLevel targetLevel) {
            if (registeredLevel == targetLevel) {
                return;
            }
            targetLevel.registerCapabilityListener(target.pos(), this);
            registeredLevel = targetLevel;
        }
    }

    private List<WirelessEnergyAPI.Target> getValidTargets(ServerLevel serverLevel) {
        // O(1) version check replaces a per-tick 64-element record-equals
        // comparison: the host bumps its connection version on every list
        // mutation, so when nothing has changed we only do an int compare.
        int version = host.getConnectionVersion();
        if (cachedConnectionVersion != version) {
            var connections = host.getConnections();
            var rebuilt = new ArrayList<WirelessEnergyAPI.Target>(connections.size());
            for (var conn : connections) {
                rebuilt.add(new WirelessEnergyAPI.Target(conn.dimension(), conn.pos(), conn.boundFace()));
            }
            cachedConnections = List.copyOf(connections);
            cachedConnectionTargets = List.copyOf(rebuilt);
            cachedValidTargets.clear();
            cachedValidTargetSet.clear();
            validTargetsCacheTick = Long.MIN_VALUE;
            cachedConnectionVersion = version;
        }

        long gameTime = serverLevel.getGameTime();
        // First-call seed + periodic 5 s sweep (offset per-BE so that 64
        // wireless supplies don't all spike the same tick). Cap-invalidation
        // listeners deliver real-time updates between sweeps.
        if (validTargetsCacheTick == Long.MIN_VALUE
                || (gameTime + revalidationOffset) % REVALIDATION_INTERVAL == 0L) {
            nextValidTargets.clear();
            nextValidTargetSet.clear();
            invalidConnections.clear();

            var server = serverLevel.getServer();
            for (int i = 0; i < cachedConnectionTargets.size(); i++) {
                var target = cachedConnectionTargets.get(i);
                ServerLevel targetLevel = server.getLevel(target.dimension());
                if (targetLevel == null) {
                    invalidConnections.add(cachedConnections.get(i));
                    continue;
                }
                if (!targetLevel.isLoaded(target.pos())) {
                    continue;
                }
                if (targetLevel.getBlockEntity(target.pos()) == null) {
                    invalidConnections.add(cachedConnections.get(i));
                    continue;
                }

                nextValidTargets.add(target);
                nextValidTargetSet.add(target);
            }

            if (!invalidConnections.isEmpty()) {
                host.removeConnections(invalidConnections);
                invalidConnections.clear();
                return getValidTargets(serverLevel);
            }

            if (!cachedValidTargets.equals(nextValidTargets)) {
                cachedValidTargets.clear();
                cachedValidTargets.addAll(nextValidTargets);
                cachedValidTargetSet.clear();
                cachedValidTargetSet.addAll(nextValidTargetSet);
                validTargetsVersion++;
                rebuildConnectionState();
            }
            validTargetsCacheTick = gameTime;
        }

        return cachedValidTargets;
    }

    /**
     * Rebuild the index-aligned snapshots ({@link #connectionTargetCaches} and
     * {@link #connectionTicketExpiry}) whenever the valid target set changes.
     * Cache instances are reused from {@link #targetCachePool} so that
     * NeoForge's capability invalidation listener (registered inside each
     * cache) is not unbound on transient list reorderings.
     */
    private void rebuildConnectionState() {
        int n = cachedValidTargets.size();
        targetCachePool.keySet().retainAll(cachedValidTargetSet);

        if (connectionTargetCaches.length < n) {
            connectionTargetCaches = new BlockEnergyTargetCache[Math.max(n, OVERLOAD_MAX_CONNECTIONS)];
        }
        for (int i = 0; i < n; i++) {
            connectionTargetCaches[i] = targetCachePool.computeIfAbsent(
                    cachedValidTargets.get(i), BlockEnergyTargetCache::new);
        }
        for (int i = n; i < connectionTargetCaches.length; i++) {
            connectionTargetCaches[i] = null;
        }

        if (connectionTicketExpiry.length < n) {
            connectionTicketExpiry = new long[Math.max(n, OVERLOAD_MAX_CONNECTIONS)];
        } else if (connectionTicketExpiry.length > 0) {
            Arrays.fill(connectionTicketExpiry, 0L);
        }

        normalWheelDirty = true;
    }

    private void clearTargetCaches() {
        targetCachePool.clear();
        if (connectionTargetCaches.length > 0) {
            Arrays.fill(connectionTargetCaches, null);
        }
    }

    private IntWheelSlot pollNormalWheel() {
        IntWheelSlot slot = normalEnergyWheel[normalWheelPointer];
        if (slot.size == 0) {
            return slot;
        }
        IntWheelSlot spare = spareNormalWheelSlot;
        spare.size = 0;
        normalEnergyWheel[normalWheelPointer] = spare;
        spareNormalWheelSlot = slot;
        return slot;
    }

    private void scheduleNormalEntry(int targetIndex) {
        int delay = connectionTargetCaches[targetIndex].scheduleDelay;
        int slot = (normalWheelPointer + delay) & ENERGY_WHEEL_SLOTS_MASK;
        normalEnergyWheel[slot].add(targetIndex);
    }

    private void requeueNormalEntriesSoon(IntWheelSlot due, int startIndex) {
        if (startIndex >= due.size) {
            return;
        }
        int slotIdx = (normalWheelPointer + 1) & ENERGY_WHEEL_SLOTS_MASK;
        IntWheelSlot targetSlot = normalEnergyWheel[slotIdx];
        int[] dueData = due.data;
        int dueSize = due.size;
        for (int i = startIndex; i < dueSize; i++) {
            targetSlot.add(dueData[i]);
        }
    }

    private void rebuildNormalWheel() {
        for (var slot : normalEnergyWheel) {
            slot.clear();
        }
        spareNormalWheelSlot.clear();
        normalWheelPointer = 0;
        int size = cachedValidTargets.size();
        for (int i = 0; i < size; i++) {
            // First placement: distribute across the wheel so we don't process
            // all 64 connections in the same tick. Subsequent reschedules go
            // through scheduleNormalEntry which uses cache.scheduleDelay alone,
            // preserving the per-target ~5 tick refill cadence.
            int initialSlot = (normalWheelPointer + 1 + i) & ENERGY_WHEEL_SLOTS_MASK;
            normalEnergyWheel[initialSlot].add(i);
        }
        normalWheelTargetsVersion = validTargetsVersion;
        normalWheelDirty = false;
    }

    private void resetNormalWheel() {
        for (var slot : normalEnergyWheel) {
            slot.clear();
        }
        normalBatchDemand = new long[0];
        normalBatchEnergyTargets = new TargetAccess[0];
        spareNormalWheelSlot.clear();
        normalWheelPointer = 0;
        normalWheelTargetsVersion = -1;
        normalWheelDirty = true;
    }

    private void ensureNormalBatchCapacity(int size) {
        // Both arrays are always grown together — the legacy second branch
        // that only widened normalBatchEnergyTargets was unreachable.
        if (normalBatchDemand.length < size) {
            normalBatchDemand = new long[size];
            normalBatchEnergyTargets = new TargetAccess[size];
        }
    }

    /**
     * Updates the cached schedule delay for a target and re-queues it into the
     * wheel in a single pass. Fuses the previous {@code adjustNormalDelay} and
     * {@code scheduleNormalEntry} call pair, and inlines
     * {@link IntWheelSlot#add} so the modulate-pass tail of {@code tickNormal}
     * is one straight-line method instead of three nested calls — JIT
     * profiling kept attributing the inner array store back to {@code add}'s
     * frame, so collapsing the call chain is the most reliable way to make
     * that go away.
     */
    private void adjustAndScheduleNormalEntry(int targetIndex, long pushed) {
        BlockEnergyTargetCache cache = connectionTargetCaches[targetIndex];
        int delay = cache.scheduleDelay;
        if (pushed > 0L) {
            delay = delay > ENERGY_DELAY_MEAN ? delay / 2 : delay - 1;
        } else {
            delay++;
        }
        if (delay < ENERGY_DELAY_MIN) delay = ENERGY_DELAY_MIN;
        else if (delay > ENERGY_DELAY_MAX) delay = ENERGY_DELAY_MAX;
        cache.scheduleDelay = delay;
        int slot = (normalWheelPointer + delay) & ENERGY_WHEEL_SLOTS_MASK;
        IntWheelSlot ws = normalEnergyWheel[slot];
        int[] data = ws.data;
        int size = ws.size;
        if (size >= data.length) {
            data = Arrays.copyOf(data, data.length << 1);
            ws.data = data;
        }
        data[size] = targetIndex;
        ws.size = size + 1;
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
