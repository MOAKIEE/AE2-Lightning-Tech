package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.helpers.MachineSource;
import appeng.core.settings.TickRates;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessStrategy;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

/**
 * Extended pattern-provider logic that adds a wireless dispatch path.
 * <p>
 * In {@link ProviderMode#NORMAL} every call delegates to the vanilla
 * {@link PatternProviderLogic} implementation (incl. ticker, sendList, etc.).
 * <p>
 * In {@link ProviderMode#WIRELESS} the {@link #pushPattern} override performs
 * SINGLE_TARGET round-robin dispatch over the host's wireless connection list.
 */
public class OverloadedPatternProviderLogic extends PatternProviderLogic {

    private final OverloadedPatternProviderBlockEntity overloadedHost;
    private final IManagedGridNode gridNode;
    private final IActionSource wirelessSource;

    // ---- wireless dispatch state ------------------------------------------------

    /** Items left over from a wireless push (race-condition overflow). */
    private final List<GenericStack> wirelessSendList = new ArrayList<>();

    /** The connection that still has pending overflow items. */
    @Nullable
    private WirelessConnection wirelessSendConn;

    /** How many consecutive flush attempts have failed (target gone / full). */
    private int wirelessFlushFailures = 0;

    /** After this many failed flushes the overflow is returned to the network. */
    private static final int MAX_FLUSH_FAILURES = 40; // ~2 seconds at 20 TPS

    /** Round-robin index across the *valid* connection list for SINGLE_TARGET. */
    private int wirelessRoundRobin = 0;

    /** Per-connection push counts for EVEN_DISTRIBUTION load balancing. */
    private final Map<WirelessConnection, Integer> distributionCounts = new HashMap<>();

    /** Weak capability cache for wireless energy targets to avoid repeated capability lookup churn. */
    private final Map<WirelessConnection, EnergyStorageCacheEntry> energyStorageCache = new HashMap<>();

    /** Cached output filter derived from loaded patterns. */
    @Nullable
    private AllowedOutputFilter cachedOutputFilter;

    /** Marks the cached output filter dirty when patterns change. */
    private boolean outputFilterDirty = true;

    // ---- auto-return state (per-machine exponential backoff) --------------------

    /** Per-machine key for backoff maps without transient String allocation. */
    private record MachineId(ResourceKey<Level> dimension, long posLong, Direction face) {}

    /** Per-machine: game-tick at which the next poll is allowed. */
    private final Map<MachineId, Long> machineNextPoll = new HashMap<>();

    /** Per-machine: current backoff interval in ticks. */
    private final Map<MachineId, Integer> machineBackoff = new HashMap<>();

    /** Minimum polling interval (reset value after a successful extraction). */
    private static final int BACKOFF_MIN = 10;    // 0.5 second

    /** Maximum polling interval (cap for exponential growth). */
    private static final int BACKOFF_MAX = 1200;  // 60 seconds

    /** AE2 grid tick range for the overloaded provider's custom scheduler. */
    private static final int GRID_TICK_MIN = 1;
    private static final int GRID_TICK_MAX = 20;

    /** Refresh the validated wireless-connection view at most once per second. */
    private static final int VALIDATE_INTERVAL = 20;

    /** Short sleep for the wireless energy path when ME is empty or all targets are saturated. */
    private static final int ENERGY_COOLDOWN_TICKS = 10;

    /** Sentinel: the target can accept energy, but the ME network currently cannot provide it. */
    private static final int NETWORK_EMPTY = -1;

    /** Last game tick when wireless connections were validated / collected. */
    private long lastConnectionValidation = -1;

    /** Cached list of valid wireless connections (shared by energy + auto-return). */
    private List<WirelessConnection> validConnectionsCache = List.of();

    /** Game tick at which validConnectionsCache was last refreshed. */
    private long validConnectionsCacheTick = -1;

    /** External host changes force the next wireless lookup to rebuild the cache immediately. */
    private boolean connectionsDirty = true;

    /** External state changes force the energy path to bypass cooldown once. */
    private boolean energyWorkDirty = true;

    /** Current short-term cooldown reason for the wireless energy path. */
    private EnergyCooldownReason wirelessEnergyCooldownReason = EnergyCooldownReason.NONE;

    /** Earliest game tick when the wireless energy path should retry after a cooldown. */
    private long wirelessEnergyCooldownUntilTick = -1;

    /** Single-target mode only enters saturation cooldown after a full no-progress sweep. */
    private int singleTargetEnergyMisses = 0;

    private enum EnergyCooldownReason {
        NONE,
        ME_EMPTY,
        TARGETS_SATURATED
    }

    private record EnergyStorageCacheEntry(
            WeakReference<BlockEntity> blockEntityRef,
            WeakReference<IEnergyStorage> storageRef) {
        boolean matches(BlockEntity blockEntity) {
            return blockEntityRef.get() == blockEntity;
        }

        @Nullable
        IEnergyStorage getStorage() {
            return storageRef.get();
        }
    }

    // ---- construction -----------------------------------------------------------

    public OverloadedPatternProviderLogic(IManagedGridNode mainNode,
                                          OverloadedPatternProviderBlockEntity host,
                                          int patternInventorySize) {
        super(mainNode, host, patternInventorySize);
        mainNode.addService(IGridTickable.class, new Ticker());
        this.overloadedHost = host;
        this.gridNode = mainNode;
        this.wirelessSource = new MachineSource(mainNode::getNode);
    }

    @Override
    public void updatePatterns() {
        var accessor = (PatternProviderLogicAccessor) this;
        var patterns = accessor.getPatterns();
        var patternInputs = accessor.getPatternInputs();
        var inventory = accessor.getPatternInventory();

        patterns.clear();
        patternInputs.clear();

        var level = overloadedHost.getLevel();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var patternStack = inventory.getStackInSlot(slot);
            var details = PatternDetailsHelper.decodePattern(patternStack, level);
            if (details == null) {
                continue;
            }

            patterns.add(details);
            for (var input : details.getInputs()) {
                for (var possibleInput : input.getPossibleInputs()) {
                    patternInputs.add(possibleInput.what().dropSecondary());
                }
            }
        }
        outputFilterDirty = true;

        ICraftingProvider.requestUpdate(accessor.getMainNode());
        alertGridTick();
    }

    // ---- pushPattern override ---------------------------------------------------

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Always try to flush wireless overflow (handles mode-switching edge case)
        if (!wirelessSendList.isEmpty()) {
            flushWirelessSends();
        }

        if (overloadedHost.getProviderMode() == ProviderMode.NORMAL) {
            boolean result = super.pushPattern(patternDetails, inputHolder);
            if (result && overloadedHost.isAutoReturn()) {
                resetBackoffAllTargets();
            }
            if (result) {
                wakeWirelessEnergyWork();
            }
            return result;
        }
        return wirelessPushPattern(patternDetails, inputHolder);
    }

    private boolean wirelessPushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        // 1. Cannot push if overflow still pending
        if (!wirelessSendList.isEmpty()) {
            return false;
        }

        // 2. Basic pre-conditions (mirrors vanilla checks)
        if (!gridNode.isActive()) return false;
        if (!getAvailablePatterns().contains(pattern)) return false;
        if (getCraftingLockedReason() != LockCraftingMode.NONE) return false;

        // 3. Collect valid targets
        var connections = overloadedHost.getConnections();
        if (connections.isEmpty()) return false;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return false;
        var server = sl.getServer();

        var valid = new ArrayList<WirelessConnection>();
        for (var conn : connections) {
            var targetLevel = server.getLevel(conn.dimension());
            if (targetLevel != null && targetLevel.isLoaded(conn.pos())
                    && targetLevel.getBlockEntity(conn.pos()) != null) {
                valid.add(conn);
            }
        }
        if (valid.isEmpty()) return false;

        // 4. Dispatch according to strategy
        var strategy = overloadedHost.getWirelessStrategy();
        if (strategy == WirelessStrategy.EVEN_DISTRIBUTION) {
            return dispatchEvenDistribution(pattern, inputs, valid, server);
        }
        return dispatchSingleTarget(pattern, inputs, valid, server);
    }

    // ---- dispatch strategies ----------------------------------------------------

    /**
     * SINGLE_TARGET: sticky — always send to the first valid connection.
     * Only returns false if that machine refuses; never auto-rotates to others.
     */
    private boolean dispatchSingleTarget(IPatternDetails pattern, KeyCounter[] inputs,
            List<WirelessConnection> valid, net.minecraft.server.MinecraftServer server) {
        return tryPushToConnection(pattern, inputs, valid.get(0), server);
    }

    /**
     * EVEN_DISTRIBUTION: load-balanced — sort machines by accumulated push count
     * (least loaded first) and try each until one accepts.
     * <p>
     * Per pushPattern() call we still push exactly 1 copy; the "even" property
     * emerges over many consecutive calls as the least-loaded machine is always
     * preferred, naturally spreading tasks across all connected machines.
     */
    private boolean dispatchEvenDistribution(IPatternDetails pattern, KeyCounter[] inputs,
            List<WirelessConnection> valid, net.minecraft.server.MinecraftServer server) {
        // Prune counters for connections that are no longer valid
        distributionCounts.keySet().retainAll(new HashSet<>(valid));

        // Sort by push count ascending — least loaded machine first
        valid.sort(Comparator.comparingInt(c -> distributionCounts.getOrDefault(c, 0)));

        for (var conn : valid) {
            if (tryPushToConnection(pattern, inputs, conn, server)) {
                distributionCounts.merge(conn, 1, Integer::sum);
                return true;
            }
        }
        return false;
    }

    /**
     * Try to push one pattern copy to a single wireless connection.
     * On success, stores any overflow and triggers lock-mode transitions.
     */
    private boolean tryPushToConnection(IPatternDetails pattern, KeyCounter[] inputs,
            WirelessConnection conn, net.minecraft.server.MinecraftServer server) {
        var targetLevel = server.getLevel(conn.dimension());
        if (targetLevel == null) return false;

        var adapter = MachineAdapterRegistry.find(targetLevel, conn.pos());
        if (adapter == null) return false;

        boolean blocking = isBlocking();
        var patternInputs = ((PatternProviderLogicAccessor) this).getPatternInputs();
        var result = adapter.pushCopies(
                targetLevel, conn.pos(), conn.boundFace(),
                pattern, inputs, 1,
                blocking, patternInputs, wirelessSource);
        if (result.acceptedCopies() == 0) return false;

        // Track overflow for later flushing
        if (!result.overflow().isEmpty()) {
            wirelessSendList.addAll(result.overflow());
            wirelessSendConn = conn;
        }

        // Trigger lock-mode transitions (lock-until-pulse / lock-until-result)
        ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(pattern);
        wakeWirelessEnergyWork();

        // Reset backoff so auto-return checks this machine promptly
        if (overloadedHost.isAutoReturn()) {
            var mkey = machineKey(targetLevel, conn.pos(), conn.boundFace());
            machineBackoff.put(mkey, BACKOFF_MIN);
            machineNextPoll.put(mkey, targetLevel.getGameTime() + BACKOFF_MIN);
        }
        return true;
    }

    // ---- overflow flush ---------------------------------------------------------

    private void flushWirelessSends() {
        if (wirelessSendConn == null) {
            wirelessSendList.clear();
            wirelessFlushFailures = 0;
            return;
        }

        boolean flushed = false;
        var level = overloadedHost.getLevel();
        if (level instanceof ServerLevel sl) {
            var targetLevel = sl.getServer().getLevel(wirelessSendConn.dimension());
            if (targetLevel != null) {
                var adapter = MachineAdapterRegistry.find(targetLevel, wirelessSendConn.pos());
                if (adapter != null) {
                    adapter.flushOverflow(
                            targetLevel, wirelessSendConn.pos(), wirelessSendConn.boundFace(),
                            wirelessSendList, wirelessSource);
                    if (wirelessSendList.isEmpty()) {
                        flushed = true;
                    }
                }
            }
        }

        if (flushed) {
            wirelessSendConn = null;
            wirelessFlushFailures = 0;
        } else {
            wirelessFlushFailures++;
            if (wirelessFlushFailures >= MAX_FLUSH_FAILURES) {
                // Target unreachable for too long — dump overflow back to network
                // to avoid permanently blocking the crafting CPU.
                returnOverflowToNetwork();
            }
        }
    }

    /**
     * Return stuck overflow items to the ME network as a last resort.
     * Items that the network cannot absorb are dropped as entity items.
     */
    private void returnOverflowToNetwork() {
        var drops = new ArrayList<ItemStack>();
        gridNode.ifPresent((grid, node) -> {
            var storage = grid.getStorageService().getInventory();
            var it = wirelessSendList.listIterator();
            while (it.hasNext()) {
                var stack = it.next();
                var inserted = storage.insert(stack.what(), stack.amount(),
                        appeng.api.config.Actionable.MODULATE, wirelessSource);
                if (inserted >= stack.amount()) {
                    it.remove();
                } else if (inserted > 0) {
                    it.set(new GenericStack(stack.what(), stack.amount() - inserted));
                }
            }
        });
        // Anything left after network insert → collect and spawn as item drops
        if (!wirelessSendList.isEmpty()) {
            for (var stack : wirelessSendList) {
                stack.what().addDrops(stack.amount(), drops,
                        overloadedHost.getLevel(), overloadedHost.getBlockPos());
            }
            wirelessSendList.clear();
        }
        for (var drop : drops) {
            var level = overloadedHost.getLevel();
            var pos = overloadedHost.getBlockPos();
            if (level != null) {
                net.minecraft.world.level.block.Block.popResource(level, pos, drop);
            }
        }
        wirelessSendConn = null;
        wirelessFlushFailures = 0;
    }

    // ---- auto-return (full-scan + per-machine exponential backoff) ---------------

    /**
     * Called every server tick (via BlockEntityTicker).
     * <p>
     * Iterates <b>all</b> connected machines each tick, but only actually polls
     * a machine when its individual backoff timer has elapsed.
     * <ul>
     *   <li>Extraction found → reset that machine's interval to {@link #BACKOFF_MIN}.</li>
     *   <li>Empty poll → double the interval (capped at {@link #BACKOFF_MAX}).</li>
     * </ul>
     * Only items whose {@link AEKey} matches a loaded pattern output are extracted.
     */
    public void tickAutoReturn() {
        // Top-level short-circuit: skip entire tick when there is no work at all
        if (!hasAnyTickWork()) return;

        tickWirelessInductionEnergy();

        if (!overloadedHost.isAutoReturn()) return;
        if (!gridNode.isActive()) return;

        // Build the output filter from all loaded patterns
        var allowedOutputs = getOrBuildOutputFilter();
        if (allowedOutputs.isEmpty()) return;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;

        long gameTick = sl.getGameTime();

        if (overloadedHost.getProviderMode() == ProviderMode.NORMAL) {
            autoReturnNormal(sl, allowedOutputs, gameTick);
        } else {
            autoReturnWireless(sl, allowedOutputs, gameTick);
        }
    }

    /**
     * Quick check: is there any reason to run the server tick at all?
     * Returns false when NORMAL mode + autoReturn off + no wireless overflow,
     * allowing the tick to be completely skipped.
     */
    public boolean hasAnyTickWork() {
        // Wireless overflow always needs flushing
        if (!wirelessSendList.isEmpty()) return true;
        // Wireless mode energy induction needs ticking
        if (overloadedHost.getProviderMode() == ProviderMode.WIRELESS) return true;
        // Auto-return needs ticking
        if (overloadedHost.isAutoReturn()) return true;
        return false;
    }

    private AllowedOutputFilter getOrBuildOutputFilter() {
        if (!outputFilterDirty && cachedOutputFilter != null) {
            return cachedOutputFilter;
        }

        cachedOutputFilter = collectPatternOutputFilter();
        outputFilterDirty = false;
        return cachedOutputFilter;
    }

    /**
     * Collect all output AEKeys from every pattern loaded in this provider.
     */
    private AllowedOutputFilter collectPatternOutputFilter() {
        var filter = new AllowedOutputFilter();
        for (var pattern : getAvailablePatterns()) {
            if (pattern instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
                for (var output : overloadDetails.overloadPatternDetailsView().outputs()) {
                    var key = AEItemKey.of(output.template());
                    if (output.matchMode() == MatchMode.ID_ONLY) {
                        filter.allowIdOnly(BuiltInRegistries.ITEM.getKey(output.template().getItem()));
                    } else if (key != null) {
                        filter.allowStrict(key);
                    }
                }
                continue;
            }

            for (var output : pattern.getOutputs()) {
                filter.allowStrict(output.what());
            }
        }
        return filter;
    }

    private void autoReturnNormal(ServerLevel level, AllowedOutputFilter allowedOutputs, long gameTick) {
        var providerPos = overloadedHost.getBlockPos();
        for (var dir : overloadedHost.getTargets()) {
            var targetPos = providerPos.relative(dir);
            var key = machineKey(level, targetPos, dir.getOpposite());

            if (gameTick < machineNextPoll.getOrDefault(key, 0L)) continue;

            var adapter = MachineAdapterRegistry.find(level, targetPos);
            if (adapter == null) continue;

            var face = dir.getOpposite();
            var outputs = adapter.extractOutputs(level, targetPos, face, allowedOutputs, wirelessSource);
            returnToNetwork(outputs);
            updateBackoff(key, gameTick, !outputs.isEmpty());
        }
    }

    private void autoReturnWireless(ServerLevel sl, AllowedOutputFilter allowedOutputs, long gameTick) {
        var valid = getOrRefreshValidConnections(sl, gameTick);
        for (var conn : valid) {
            var targetLevel = sl.getServer().getLevel(conn.dimension());
            if (targetLevel == null) continue;

            var key = machineKey(targetLevel, conn.pos(), conn.boundFace());
            if (gameTick < machineNextPoll.getOrDefault(key, 0L)) continue;

            var adapter = MachineAdapterRegistry.find(targetLevel, conn.pos());
            if (adapter == null) continue;

            var outputs = adapter.extractOutputs(
                    targetLevel, conn.pos(), conn.boundFace(), allowedOutputs, wirelessSource);
            returnToNetwork(outputs);
            updateBackoff(key, gameTick, !outputs.isEmpty());
        }
    }

    /** Unified machine key without transient String allocation. */
    private static MachineId machineKey(ServerLevel level, BlockPos pos, Direction face) {
        return machineKey(level.dimension(), pos, face);
    }

    private static MachineId machineKey(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        return new MachineId(dimension, pos.asLong(), face);
    }

    /**
     * Reset backoff for all adjacent machine targets (NORMAL mode).
     * Called after a successful pushPattern so auto-return starts
     * checking promptly.
     */
    private void resetBackoffAllTargets() {
        var lvl = overloadedHost.getLevel();
        if (!(lvl instanceof ServerLevel sl)) return;
        long gameTick = sl.getGameTime();
        var providerPos = overloadedHost.getBlockPos();
        for (var dir : overloadedHost.getTargets()) {
            var key = machineKey(sl, providerPos.relative(dir), dir.getOpposite());
            machineBackoff.put(key, BACKOFF_MIN);
            machineNextPoll.put(key, gameTick + BACKOFF_MIN);
        }
    }

    /**
     * After polling a machine, update its backoff state.
     *
     * @param foundItems true if at least one output was extracted
     */
    private void updateBackoff(MachineId key, long gameTick, boolean foundItems) {
        int interval;
        if (foundItems) {
            interval = BACKOFF_MIN;
        } else {
            int current = machineBackoff.getOrDefault(key, BACKOFF_MIN);
            interval = Math.min(current * 2, BACKOFF_MAX);
        }
        machineBackoff.put(key, interval);
        machineNextPoll.put(key, gameTick + interval);
    }

    private void returnToNetwork(List<GenericStack> outputs) {
        if (outputs.isEmpty()) return;
        gridNode.ifPresent((grid, node) -> {
            var storage = grid.getStorageService().getInventory();
            for (var stack : outputs) {
                storage.insert(stack.what(), stack.amount(),
                        appeng.api.config.Actionable.MODULATE, wirelessSource);
            }
        });
    }

    private void tickWirelessInductionEnergy() {
        if (overloadedHost.getProviderMode() != ProviderMode.WIRELESS) {
            return;
        }
        if (!gridNode.isActive() || !isInductionCardInstalled()) {
            return;
        }

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return;
        }

        // Use cached reflection results instead of per-tick reflection
        var feKey = CACHED_APPFLUX_FE_KEY;
        if (feKey == null) {
            return;
        }

        int transferBudget = CACHED_APPFLUX_TRANSFER_RATE;
        if (transferBudget <= 0) {
            return;
        }

        long gameTick = sl.getGameTime();
        if (isWirelessEnergyCooldownActive(gameTick, feKey)) {
            return;
        }

        energyWorkDirty = false;
        // Reuse unified valid-connections cache (no duplicate clearInvalidConnections)
        var valid = getOrRefreshValidConnections(sl, gameTick);
        if (valid.isEmpty()) {
            singleTargetEnergyMisses = 0;
            return;
        }

        WirelessEnergyTickResult result;
        if (overloadedHost.getWirelessStrategy() == WirelessStrategy.EVEN_DISTRIBUTION) {
            result = distributeWirelessEnergyEven(valid, feKey, transferBudget);
        } else {
            result = distributeWirelessEnergySingle(valid, feKey, transferBudget);
        }

        switch (result) {
            case TRANSFERRED -> clearWirelessEnergyCooldown();
            case NO_ME_ENERGY -> enterWirelessEnergyCooldown(gameTick, EnergyCooldownReason.ME_EMPTY);
            case TARGETS_SATURATED ->
                    enterWirelessEnergyCooldown(gameTick, EnergyCooldownReason.TARGETS_SATURATED);
            case NO_PROGRESS -> {
                // Single-target mode is still rotating through the connection set.
            }
        }
    }

    /**
     * Returns a cached list of valid wireless connections.
     * The cache is refreshed at most once per {@link #VALIDATE_INTERVAL} ticks.
     * Both the energy-induction path and auto-return path share this cache
     * to avoid duplicate world queries within a single tick.
     */
    private List<WirelessConnection> getOrRefreshValidConnections(ServerLevel providerLevel, long gameTick) {
        if (!connectionsDirty && gameTick - validConnectionsCacheTick < VALIDATE_INTERVAL) {
            return validConnectionsCache;
        }

        // Validate + collect in a single pass
        overloadedHost.clearInvalidConnections();
        var server = providerLevel.getServer();
        var valid = new ArrayList<WirelessConnection>();
        for (var conn : overloadedHost.getConnections()) {
            var targetLevel = server.getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                continue;
            }
            if (targetLevel.getBlockEntity(conn.pos()) == null) {
                continue;
            }
            valid.add(conn);
        }
        validConnectionsCache = List.copyOf(valid);
        validConnectionsCacheTick = gameTick;
        lastConnectionValidation = gameTick;
        connectionsDirty = false;
        singleTargetEnergyMisses = 0;
        return validConnectionsCache;
    }

    private WirelessEnergyTickResult distributeWirelessEnergySingle(List<WirelessConnection> valid,
            AEKey feKey, int transferBudget) {
        if (valid.isEmpty() || transferBudget <= 0) {
            return WirelessEnergyTickResult.NO_PROGRESS;
        }

        int index = Math.floorMod(wirelessRoundRobin, valid.size());
        wirelessRoundRobin = index + 1;
        int sent = transferEnergyToConnection(valid.get(index), feKey, transferBudget);
        if (sent > 0) {
            singleTargetEnergyMisses = 0;
            return WirelessEnergyTickResult.TRANSFERRED;
        }
        if (sent == NETWORK_EMPTY) {
            singleTargetEnergyMisses = 0;
            return WirelessEnergyTickResult.NO_ME_ENERGY;
        }

        singleTargetEnergyMisses++;
        if (singleTargetEnergyMisses >= valid.size()) {
            singleTargetEnergyMisses = 0;
            return WirelessEnergyTickResult.TARGETS_SATURATED;
        }
        return WirelessEnergyTickResult.NO_PROGRESS;
    }

    private WirelessEnergyTickResult distributeWirelessEnergyEven(List<WirelessConnection> valid,
            AEKey feKey, int transferBudget) {
        if (valid.isEmpty() || transferBudget <= 0) {
            return WirelessEnergyTickResult.NO_PROGRESS;
        }

        int remaining = transferBudget;
        int targetsLeft = valid.size();
        boolean transferredAny = false;
        for (var conn : valid) {
            if (remaining <= 0) {
                return WirelessEnergyTickResult.TRANSFERRED;
            }

            int share = Math.max(1, remaining / targetsLeft);
            int sent = transferEnergyToConnection(conn, feKey, share);
            if (sent == NETWORK_EMPTY) {
                return transferredAny
                        ? WirelessEnergyTickResult.TRANSFERRED
                        : WirelessEnergyTickResult.NO_ME_ENERGY;
            }
            if (sent > 0) {
                transferredAny = true;
                remaining -= sent;
            }
            targetsLeft--;
        }
        if (transferredAny) {
            return WirelessEnergyTickResult.TRANSFERRED;
        }
        return WirelessEnergyTickResult.TARGETS_SATURATED;
    }

    private int transferEnergyToConnection(WirelessConnection conn, AEKey feKey, int maxToSend) {
        if (maxToSend <= 0) {
            return 0;
        }

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel providerLevel)) {
            return 0;
        }

        var targetLevel = providerLevel.getServer().getLevel(conn.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
            return 0;
        }

        IEnergyStorage storage = resolveEnergyStorage(targetLevel, conn);
        if (storage == null) {
            return 0;
        }

        int canReceive = storage.receiveEnergy(maxToSend, true);
        if (canReceive <= 0) {
            return 0;
        }

        long extracted = extractAppliedFluxEnergy(feKey, canReceive);
        if (extracted <= 0) {
            return NETWORK_EMPTY;
        }

        int extractedInt = (int) Math.min(Integer.MAX_VALUE, extracted);
        int accepted = storage.receiveEnergy(extractedInt, false);
        if (accepted < extractedInt) {
            long refund = extractedInt - accepted;
            if (refund > 0) {
                insertAppliedFluxEnergy(feKey, refund);
            }
        }
        return accepted;
    }

    public void onHostStateChanged() {
        invalidateValidConnectionsCache();
        invalidateEnergyStorageCache();
        wakeWirelessEnergyWork();
    }

    public void onPersistentStateChanged() {
        wakeWirelessEnergyWork();
    }

    public void onNeighborChanged() {
        invalidateEnergyStorageCache();
        wakeWirelessEnergyWork();
    }

    private boolean hasCombinedGridTickWork() {
        var accessor = (PatternProviderLogicAccessor) this;
        return accessor.invokeHasWorkToDo() || hasAnyTickWork();
    }

    private boolean hasActiveOverloadedTickWork(long gameTick) {
        if (!wirelessSendList.isEmpty()) {
            return true;
        }
        if (shouldTickWirelessEnergyNow(gameTick)) {
            return true;
        }
        return shouldPollAutoReturnNow(gameTick);
    }

    private boolean shouldTickWirelessEnergyNow(long gameTick) {
        if (overloadedHost.getProviderMode() != ProviderMode.WIRELESS) {
            return false;
        }
        if (!gridNode.isActive() || !isInductionCardInstalled()) {
            return false;
        }
        if (CACHED_APPFLUX_FE_KEY == null || CACHED_APPFLUX_TRANSFER_RATE <= 0) {
            return false;
        }
        return energyWorkDirty
                || wirelessEnergyCooldownReason == EnergyCooldownReason.NONE
                || gameTick >= wirelessEnergyCooldownUntilTick;
    }

    private boolean shouldPollAutoReturnNow(long gameTick) {
        if (!overloadedHost.isAutoReturn() || !gridNode.isActive()) {
            return false;
        }
        if (getOrBuildOutputFilter().isEmpty()) {
            return false;
        }
        return getNextAutoReturnPollTick() <= gameTick;
    }

    private long getNextAutoReturnPollTick() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return Long.MAX_VALUE;
        }

        long nextPollTick = Long.MAX_VALUE;
        if (overloadedHost.getProviderMode() == ProviderMode.NORMAL) {
            var providerPos = overloadedHost.getBlockPos();
            var targets = overloadedHost.getTargets();
            if (targets.isEmpty()) {
                return Long.MAX_VALUE;
            }

            for (var dir : targets) {
                var key = machineKey(sl.dimension(), providerPos.relative(dir), dir.getOpposite());
                nextPollTick = Math.min(nextPollTick, machineNextPoll.getOrDefault(key, 0L));
            }
            return nextPollTick;
        }

        var connections = overloadedHost.getConnections();
        if (connections.isEmpty()) {
            return Long.MAX_VALUE;
        }

        for (var conn : connections) {
            var key = machineKey(conn.dimension(), conn.pos(), conn.boundFace());
            nextPollTick = Math.min(nextPollTick, machineNextPoll.getOrDefault(key, 0L));
        }
        return nextPollTick;
    }

    private void alertGridTick() {
        gridNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private void invalidateValidConnectionsCache() {
        connectionsDirty = true;
        validConnectionsCache = List.of();
        validConnectionsCacheTick = -1;
        lastConnectionValidation = -1;
        singleTargetEnergyMisses = 0;
    }

    private void invalidateEnergyStorageCache() {
        energyStorageCache.clear();
    }

    private void wakeWirelessEnergyWork() {
        energyWorkDirty = true;
        clearWirelessEnergyCooldown();
        alertGridTick();
    }

    private void clearWirelessEnergyCooldown() {
        wirelessEnergyCooldownReason = EnergyCooldownReason.NONE;
        wirelessEnergyCooldownUntilTick = -1;
    }

    private void enterWirelessEnergyCooldown(long gameTick, EnergyCooldownReason reason) {
        clearWirelessEnergyCooldown();
        wirelessEnergyCooldownReason = reason;
        wirelessEnergyCooldownUntilTick = gameTick + ENERGY_COOLDOWN_TICKS;
    }

    private boolean isWirelessEnergyCooldownActive(long gameTick, AEKey feKey) {
        if (energyWorkDirty || wirelessEnergyCooldownReason == EnergyCooldownReason.NONE) {
            return false;
        }
        if (gameTick >= wirelessEnergyCooldownUntilTick) {
            clearWirelessEnergyCooldown();
            return false;
        }
        if (wirelessEnergyCooldownReason == EnergyCooldownReason.ME_EMPTY
                && hasAppliedFluxEnergyAvailable(feKey)) {
            wakeWirelessEnergyWork();
            return false;
        }
        return true;
    }

    private boolean hasAppliedFluxEnergyAvailable(AEKey feKey) {
        final boolean[] available = {false};
        gridNode.ifPresent((grid, node) -> available[0] = grid.getStorageService().getInventory().extract(
                feKey,
                1,
                Actionable.SIMULATE,
                wirelessSource) > 0);
        return available[0];
    }

    @Nullable
    private IEnergyStorage resolveEnergyStorage(ServerLevel targetLevel, WirelessConnection conn) {
        BlockEntity blockEntity = targetLevel.getBlockEntity(conn.pos());
        if (blockEntity == null) {
            energyStorageCache.remove(conn);
            return null;
        }

        var cached = energyStorageCache.get(conn);
        if (cached != null && cached.matches(blockEntity)) {
            var storage = cached.getStorage();
            if (storage != null) {
                return storage;
            }
        }

        IEnergyStorage storage = targetLevel.getCapability(
                Capabilities.EnergyStorage.BLOCK,
                conn.pos(),
                conn.boundFace());
        if (storage == null) {
            energyStorageCache.remove(conn);
            return null;
        }

        energyStorageCache.put(conn, new EnergyStorageCacheEntry(
                new WeakReference<>(blockEntity),
                new WeakReference<>(storage)));
        return storage;
    }

    private boolean isInductionCardInstalled() {
        Item card = getAppliedFluxInductionCard();
        if (card == null) {
            return false;
        }

        if (this instanceof IUpgradeableObject upgradeableLogic) {
            return upgradeableLogic.getUpgrades().isInstalled(card);
        }
        return false;
    }

    private long extractAppliedFluxEnergy(AEKey feKey, long amount) {
        if (amount <= 0) {
            return 0;
        }
        final long[] extracted = {0};
        gridNode.ifPresent((grid, node) -> extracted[0] = grid.getStorageService().getInventory().extract(
                feKey,
                amount,
                Actionable.MODULATE,
                wirelessSource));
        return extracted[0];
    }

    private void insertAppliedFluxEnergy(AEKey feKey, long amount) {
        if (amount <= 0) {
            return;
        }
        gridNode.ifPresent((grid, node) -> grid.getStorageService().getInventory().insert(
                feKey,
                amount,
                Actionable.MODULATE,
                wirelessSource));
    }

    private static final ResourceLocation APPFLUX_INDUCTION_CARD_ID =
            ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
    private static final Item APPFLUX_INDUCTION_CARD =
            BuiltInRegistries.ITEM.get(APPFLUX_INDUCTION_CARD_ID);

    // ---- Cached reflection results (resolved once at class-load, never per-tick) ----

    /** Cached AEKey for Applied Flux FE energy type. Null if Applied Flux is not loaded. */
    @Nullable
    private static final AEKey CACHED_APPFLUX_FE_KEY;

    /** Cached transfer rate from Applied Flux config. 0 if not available. */
    private static final int CACHED_APPFLUX_TRANSFER_RATE;

    private enum WirelessEnergyTickResult {
        TRANSFERRED,
        NO_PROGRESS,
        NO_ME_ENERGY,
        TARGETS_SATURATED
    }

    static {
        AEKey resolvedKey = null;
        try {
            var energyTypeClass = Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) energyTypeClass.asSubclass(Enum.class);
            Object feType = Enum.valueOf(enumClass, "FE");

            var fluxKeyClass = Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey");
            var ofMethod = fluxKeyClass.getMethod("of", energyTypeClass);
            Object key = ofMethod.invoke(null, feType);
            resolvedKey = key instanceof AEKey aeKey ? aeKey : null;
        } catch (ReflectiveOperationException ignored) {
            // Applied Flux not loaded or API changed
        }
        CACHED_APPFLUX_FE_KEY = resolvedKey;

        int resolvedRate = 0;
        try {
            var configClass = Class.forName("com.glodblock.github.appflux.config.AFConfig");
            var method = configClass.getMethod("getFluxAccessorIO");
            Object result = method.invoke(null);
            if (result instanceof Number num) {
                long value = num.longValue();
                if (value > 0) {
                    resolvedRate = (int) Math.min(Integer.MAX_VALUE, value);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Applied Flux not loaded or API changed
        }
        CACHED_APPFLUX_TRANSFER_RATE = resolvedRate;
    }

    @Nullable
    private static Item getAppliedFluxInductionCard() {
        if (APPFLUX_INDUCTION_CARD == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        return APPFLUX_INDUCTION_CARD;
    }

    private class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(GRID_TICK_MIN, GRID_TICK_MAX, !hasCombinedGridTickWork());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!gridNode.isActive()) {
                return TickRateModulation.SLEEP;
            }

            var accessor = (PatternProviderLogicAccessor) OverloadedPatternProviderLogic.this;
            boolean parentDidWork = accessor.invokeDoWork();
            tickAutoReturn();
            var level = overloadedHost.getLevel();
            long gameTick = level instanceof ServerLevel sl ? sl.getGameTime() : Long.MAX_VALUE;

            if (hasActiveOverloadedTickWork(gameTick)) {
                return TickRateModulation.URGENT;
            }

            boolean parentHasWork = accessor.invokeHasWorkToDo();
            if (parentHasWork) {
                return parentDidWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
            }

            if (hasAnyTickWork()) {
                return TickRateModulation.SLOWER;
            }

            return TickRateModulation.SLEEP;
        }
    }

    // ---- isBusy override --------------------------------------------------------

    @Override
    public boolean isBusy() {
        // In WIRELESS mode, never report busy: overflow is flushed at the start of
        // pushPattern(), so the crafting system keeps calling us each tick and we
        // get a chance to drain any leftover items.
        // Parent's sendList is always empty in wireless mode (getTargets = empty).
        if (overloadedHost.getProviderMode() == ProviderMode.WIRELESS) {
            return false;
        }
        return super.isBusy();
    }

    // ---- drops & clearing -------------------------------------------------------

    @Override
    public void addDrops(List<ItemStack> drops) {
        super.addDrops(drops);
        for (var stack : wirelessSendList) {
            stack.what().addDrops(stack.amount(), drops,
                    overloadedHost.getLevel(), overloadedHost.getBlockPos());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        wirelessSendList.clear();
        wirelessSendConn = null;
        distributionCounts.clear();
        energyStorageCache.clear();
        machineNextPoll.clear();
        machineBackoff.clear();
        cachedOutputFilter = null;
        outputFilterDirty = true;
        invalidateValidConnectionsCache();
        energyWorkDirty = true;
        clearWirelessEnergyCooldown();
    }

    // ---- NBT persistence --------------------------------------------------------

    private static final String TAG_W_SEND_LIST = "WirelessSendList";
    private static final String TAG_W_SEND_CONN = "WirelessSendConn";
    private static final String TAG_W_ROUND_ROBIN = "WirelessRoundRobin";

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        tag.putInt(TAG_W_ROUND_ROBIN, wirelessRoundRobin);
        if (!wirelessSendList.isEmpty()) {
            var list = new ListTag();
            for (var stack : wirelessSendList) {
                list.add(GenericStack.writeTag(registries, stack));
            }
            tag.put(TAG_W_SEND_LIST, list);
            if (wirelessSendConn != null) {
                tag.put(TAG_W_SEND_CONN, wirelessSendConn.toTag());
            }
        }
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        wirelessRoundRobin = tag.getInt(TAG_W_ROUND_ROBIN);
        wirelessSendList.clear();
        wirelessSendConn = null;
        if (tag.contains(TAG_W_SEND_LIST, Tag.TAG_LIST)) {
            var list = tag.getList(TAG_W_SEND_LIST, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var stack = GenericStack.readTag(registries, list.getCompound(i));
                if (stack != null) {
                    wirelessSendList.add(stack);
                }
            }
        }
        if (tag.contains(TAG_W_SEND_CONN, Tag.TAG_COMPOUND)) {
            wirelessSendConn = WirelessConnection.fromTag(tag.getCompound(TAG_W_SEND_CONN));
        }
        cachedOutputFilter = null;
        outputFilterDirty = true;
        invalidateValidConnectionsCache();
        energyWorkDirty = true;
        clearWirelessEnergyCooldown();
    }
}
