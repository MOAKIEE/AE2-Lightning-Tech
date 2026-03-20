package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
import appeng.util.inv.filter.IAEItemFilter;

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

    /** Wireless round-robin return: spread all machines across this many ticks. */
    private static final int RETURN_SPREAD_TICKS = 20;

    /** Buffer flush interval: aggregate extracted items, insert every N ticks. */
    private static final int RETURN_FLUSH_INTERVAL = 5;

    /** AE2 grid tick range for the overloaded provider's custom scheduler. */
    private static final int GRID_TICK_MIN = 1;
    private static final int GRID_TICK_MAX = 20;

    /** Refresh the validated wireless-connection view at most once per second. */
    private static final int VALIDATE_INTERVAL = 20;


    /** Last game tick when wireless connections were validated / collected. */
    private long lastConnectionValidation = -1;

    /** Cached list of valid wireless connections (shared by energy + auto-return). */
    private List<WirelessConnection> validConnectionsCache = List.of();

    /** Game tick at which validConnectionsCache was last refreshed. */
    private long validConnectionsCacheTick = -1;

    /** External host changes force the next wireless lookup to rebuild the cache immediately. */
    private boolean connectionsDirty = true;

    /** Prevents double execution when both BlockEntityTicker and AE2 Grid Ticker fire in the same tick. */
    private long lastEnergyTickGameTime = -1;

    /** Wireless round-robin: index into valid connections for spread return. */
    private int returnRobinIndex = 0;

    /** Last game tick when round-robin return was executed. */
    private long lastReturnRobinTick = -1;

    /** Last game tick when single-machine pre-distribution return was executed. */
    private long lastSingleReturnTick = -1;

    /** Last game tick when the return buffer was flushed to the network. */
    private long lastReturnFlushTick = -1;

    /** Aggregated items awaiting bulk insertion into the ME network. */
    private final Map<AEKey, Long> returnBuffer = new HashMap<>();

    /** Cached result of induction card check; invalidated on state/upgrade change. */
    private boolean cachedInductionCardInstalled;
    private boolean inductionCardCacheDirty = true;

    // ---- Timing Wheel energy scheduling -------------------------------------------

    private static final int ENERGY_DELAY_MEAN = 5;
    private static final int ENERGY_DELAY_MAX  = 20;
    private static final int ENERGY_DELAY_MIN  = 1;
    private static final int WHEEL_SLOTS = ENERGY_DELAY_MAX; // 20

    static final class ScheduleEntry {
        final int connectionIndex;
        int currentDelay;
        long lastCanReceive;

        ScheduleEntry(int connectionIndex) {
            this.connectionIndex = connectionIndex;
            this.currentDelay = ENERGY_DELAY_MEAN;
            this.lastCanReceive = 0;
        }

        void update(int newDelay, long newCanReceive) {
            this.currentDelay = newDelay;
            this.lastCanReceive = newCanReceive;
        }
    }

    @SuppressWarnings("unchecked")
    private final List<ScheduleEntry>[] wheel = new ArrayList[WHEEL_SLOTS];
    {
        for (int i = 0; i < WHEEL_SLOTS; i++) wheel[i] = new ArrayList<>();
    }
    private List<ScheduleEntry> spareList = new ArrayList<>();
    private int wheelPointer = 0;
    private final List<ScheduleEntry> deferredMachines = new ArrayList<>();
    private boolean wheelDirty = true;

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

        // Keep automation behavior aligned with the menu slot restriction:
        // pattern slots only accept encoded patterns, even through external handlers.
        ((PatternProviderLogicAccessor) this).getPatternInventory().setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
                return PatternDetailsHelper.isEncodedPattern(stack);
            }
        });
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
                alertGridTick();
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

        autoReturnBeforePush(targetLevel, conn);

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
        alertGridTick();

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
        if (!wirelessSendList.isEmpty()) return true;
        if (overloadedHost.getProviderMode() == ProviderMode.WIRELESS
                && gridNode.isActive()
                && isInductionCardInstalled()
                && CACHED_APPFLUX_FE_KEY != null) return true;
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
        int total = valid.size();
        if (total == 0) return;

        long elapsed = lastReturnRobinTick >= 0 ? gameTick - lastReturnRobinTick : 1;
        lastReturnRobinTick = gameTick;

        int perTick = Math.max(1, (total + RETURN_SPREAD_TICKS - 1) / RETURN_SPREAD_TICKS);
        int toProcess = (int) Math.min((long) perTick * elapsed, total);

        for (int i = 0; i < toProcess; i++) {
            int idx = returnRobinIndex % total;
            returnRobinIndex = (returnRobinIndex + 1) % total;

            var conn = valid.get(idx);
            var targetLevel = resolveTargetLevel(sl, conn);
            if (targetLevel == null) continue;

            var adapter = MachineAdapterRegistry.find(targetLevel, conn.pos());
            if (adapter == null) continue;

            var outputs = adapter.extractOutputs(
                    targetLevel, conn.pos(), conn.boundFace(), allowedOutputs, wirelessSource);
            bufferReturn(outputs);
        }

        if (gameTick - lastReturnFlushTick >= RETURN_FLUSH_INTERVAL) {
            flushReturnBuffer();
            lastReturnFlushTick = gameTick;
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

    private void bufferReturn(List<GenericStack> outputs) {
        for (var stack : outputs) {
            returnBuffer.merge(stack.what(), stack.amount(), Long::sum);
        }
    }

    private void flushReturnBuffer() {
        if (returnBuffer.isEmpty()) return;
        gridNode.ifPresent((grid, node) -> {
            var storage = grid.getStorageService().getInventory();
            for (var entry : returnBuffer.entrySet()) {
                storage.insert(entry.getKey(), entry.getValue(),
                        appeng.api.config.Actionable.MODULATE, wirelessSource);
            }
        });
        returnBuffer.clear();
    }

    private void autoReturnBeforePush(ServerLevel sl, WirelessConnection conn) {
        if (!overloadedHost.isAutoReturn()) return;
        long gameTick = sl.getGameTime();
        if (gameTick == lastSingleReturnTick) return;
        lastSingleReturnTick = gameTick;

        var allowedOutputs = getOrBuildOutputFilter();
        if (allowedOutputs.isEmpty()) return;

        var targetLevel = resolveTargetLevel(sl, conn);
        if (targetLevel == null) return;

        var adapter = MachineAdapterRegistry.find(targetLevel, conn.pos());
        if (adapter == null) return;

        var outputs = adapter.extractOutputs(
                targetLevel, conn.pos(), conn.boundFace(), allowedOutputs, wirelessSource);
        bufferReturn(outputs);
    }

    private void tickWirelessInductionEnergy() {
        if (overloadedHost.getProviderMode() != ProviderMode.WIRELESS) return;
        if (!gridNode.isActive() || !isInductionCardInstalled()) return;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;

        var feKey = CACHED_APPFLUX_FE_KEY;
        if (feKey == null) return;
        if (CACHED_APPFLUX_TRANSFER_RATE <= 0) return;

        long gameTick = sl.getGameTime();
        if (gameTick == lastEnergyTickGameTime) return;
        int ticksElapsed = lastEnergyTickGameTime >= 0
                ? (int) Math.min(gameTick - lastEnergyTickGameTime, WHEEL_SLOTS)
                : 1;
        lastEnergyTickGameTime = gameTick;

        distributeWirelessEnergy(sl, feKey, gameTick, ticksElapsed);
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
        return validConnectionsCache;
    }

    // ---- Timing Wheel scheduling operations ----------------------------------------

    private List<ScheduleEntry> pollWheel() {
        var list = wheel[wheelPointer];
        if (list.isEmpty()) return list;
        wheel[wheelPointer] = spareList;
        spareList = list;
        return list;
    }

    private void scheduleToWheel(ScheduleEntry entry) {
        int slot = (wheelPointer + entry.currentDelay) % WHEEL_SLOTS;
        wheel[slot].add(entry);
    }

    private void rebuildWheel(int connectionCount) {
        for (var slot : wheel) slot.clear();
        deferredMachines.clear();
        for (int i = 0; i < connectionCount; i++) {
            var entry = new ScheduleEntry(i);
            scheduleToWheel(entry);
        }
        wheelDirty = false;
    }

    // ---- Wheel-based energy distribution ------------------------------------------

    private void distributeWirelessEnergy(ServerLevel sl, AEKey feKey, long currentTick,
                                          int ticksElapsed) {
        var valid = getOrRefreshValidConnections(sl, currentTick);
        if (valid.isEmpty()) return;

        wheelPointer = (wheelPointer + 1) % WHEEL_SLOTS;
        if (wheelDirty) rebuildWheel(valid.size());

        // Phase 1: deferred machines get priority
        if (!deferredMachines.isEmpty()) {
            long available = simulateAvailableFluxEnergy(feKey,
                    (long) CACHED_APPFLUX_TRANSFER_RATE * deferredMachines.size());
            if (available <= 0) return;
            processBatch(sl, valid, deferredMachines, feKey, available);
            if (!deferredMachines.isEmpty()) return;
        }

        // Phase 2: poll wheel — skip empty slots to catch up after SLOWER ticks
        for (int skip = 1; skip < ticksElapsed && wheel[wheelPointer].isEmpty(); skip++) {
            wheelPointer = (wheelPointer + 1) % WHEEL_SLOTS;
        }

        var eligible = pollWheel();
        if (eligible.isEmpty()) return;

        long available = simulateAvailableFluxEnergy(feKey,
                (long) CACHED_APPFLUX_TRANSFER_RATE * eligible.size());
        if (available <= 0) {
            deferredMachines.addAll(eligible);
            eligible.clear();
            return;
        }
        processBatch(sl, valid, eligible, feKey, available);
    }

    private void processBatch(ServerLevel sl, List<WirelessConnection> valid,
            List<ScheduleEntry> machines, AEKey feKey, long available) {
        int count = machines.size();

        // 1. Target SIM
        long totalNeeded = 0;
        long[] canReceive = new long[count];
        int[] capacity = new int[count];
        long[] storedEnergy = new long[count];
        for (int i = 0; i < count; i++) {
            var entry = machines.get(i);
            if (entry.connectionIndex >= valid.size()) continue;
            var conn = valid.get(entry.connectionIndex);
            var targetLevel = resolveTargetLevel(sl, conn);
            var storage = targetLevel != null ? resolveEnergyStorage(targetLevel, conn) : null;
            if (storage != null) {
                canReceive[i] = storage.receiveEnergy(Integer.MAX_VALUE, true);
                capacity[i] = storage.getMaxEnergyStored();
                storedEnergy[i] = storage.getEnergyStored();
                totalNeeded += canReceive[i];
            }
        }

        if (totalNeeded <= 0) {
            for (int i = 0; i < count; i++) {
                adjustDelay(machines.get(i), 0, storedEnergy[i], capacity[i]);
                scheduleToWheel(machines.get(i));
            }
            machines.clear();
            return;
        }

        // 2. ME MODULATE — batch extract
        long extracted = extractFluxEnergy(feKey, Math.min(totalNeeded, available));
        if (extracted <= 0) {
            var earlyDeferred = new ArrayList<ScheduleEntry>();
            for (int i = 0; i < count; i++) {
                var entry = machines.get(i);
                if (entry.connectionIndex >= valid.size()) continue;
                if (canReceive[i] == 0) {
                    adjustDelay(entry, 0, storedEnergy[i], capacity[i]);
                    scheduleToWheel(entry);
                } else {
                    earlyDeferred.add(entry);
                }
            }
            machines.clear();
            if (!earlyDeferred.isEmpty()) {
                deferredMachines.addAll(earlyDeferred);
            }
            return;
        }

        // 3. Target COMMIT + delay adjustment
        long remaining = extracted;
        var stillDeferred = new ArrayList<ScheduleEntry>();
        for (int i = 0; i < count; i++) {
            var entry = machines.get(i);
            if (entry.connectionIndex >= valid.size()) continue;

            if (canReceive[i] > 0 && remaining > 0) {
                long share = Math.min(canReceive[i], remaining);
                var conn = valid.get(entry.connectionIndex);
                var targetLevel = resolveTargetLevel(sl, conn);
                var storage = targetLevel != null ? resolveEnergyStorage(targetLevel, conn) : null;
                long accepted = 0;
                if (storage != null) {
                    accepted = storage.receiveEnergy(
                            (int) Math.min(share, Integer.MAX_VALUE), false);
                }
                remaining -= accepted;

                boolean budgetSufficient = (share == canReceive[i]);
                if (budgetSufficient && accepted > 0 && canReceive[i] > accepted * 2) {
                    int ratio = (int)(canReceive[i] / accepted);
                    entry.update(Math.min(entry.currentDelay * ratio, ENERGY_DELAY_MAX),
                                 canReceive[i]);
                } else if (budgetSufficient && accepted == 0) {
                    entry.update(ENERGY_DELAY_MAX, canReceive[i]);
                } else {
                    adjustDelay(entry, canReceive[i], storedEnergy[i], capacity[i]);
                }
                scheduleToWheel(entry);

            } else if (canReceive[i] == 0) {
                adjustDelay(entry, 0, storedEnergy[i], capacity[i]);
                scheduleToWheel(entry);

            } else {
                stillDeferred.add(entry);
            }
        }

        machines.clear();
        if (!stillDeferred.isEmpty()) {
            deferredMachines.addAll(stillDeferred);
        }

        // 4. Refund excess
        if (remaining > 0) {
            insertFluxEnergy(feKey, remaining);
        }
    }

    @Nullable
    private ServerLevel resolveTargetLevel(ServerLevel providerLevel, WirelessConnection conn) {
        var targetLevel = providerLevel.getServer().getLevel(conn.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) return null;
        return targetLevel;
    }

    private void adjustDelay(ScheduleEntry entry, long canReceive,
                             long storedEnergy, int machineCapacity) {
        int delay = entry.currentDelay;

        if (canReceive > entry.lastCanReceive) {
            delay = delay > ENERGY_DELAY_MEAN ? delay / 2 : delay - 1;
        } else if ((entry.lastCanReceive - canReceive) << 2 < machineCapacity) {
            if (storedEnergy * 3 <= (long) machineCapacity << 1) {
                delay = delay > ENERGY_DELAY_MEAN ? delay / 2 : delay - 1;
            } else {
                if (delay > ENERGY_DELAY_MEAN) delay--;
                else if (delay < ENERGY_DELAY_MEAN) delay++;
            }
        } else {
            delay = delay < ENERGY_DELAY_MEAN ? delay * 2 : delay + 1;
        }

        entry.update(Math.clamp(delay, ENERGY_DELAY_MIN, ENERGY_DELAY_MAX), canReceive);
    }

    public void onHostStateChanged() {
        invalidateValidConnectionsCache();
        invalidateEnergyStorageCache();
        inductionCardCacheDirty = true;
        alertGridTick();
    }

    public void onPersistentStateChanged() {
        inductionCardCacheDirty = true;
        alertGridTick();
    }

    public void onNeighborChanged() {
        invalidateEnergyStorageCache();
        alertGridTick();
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
        if (overloadedHost.getProviderMode() != ProviderMode.WIRELESS) return false;
        if (!gridNode.isActive() || !isInductionCardInstalled()) return false;
        if (CACHED_APPFLUX_FE_KEY == null || CACHED_APPFLUX_TRANSFER_RATE <= 0) return false;
        if (!deferredMachines.isEmpty()) return true;
        if (wheelDirty) return true;
        return !wheel[(wheelPointer + 1) % WHEEL_SLOTS].isEmpty();
    }

    private boolean shouldPollAutoReturnNow(long gameTick) {
        if (!overloadedHost.isAutoReturn() || !gridNode.isActive()) {
            return false;
        }
        if (getOrBuildOutputFilter().isEmpty()) {
            return false;
        }
        if (overloadedHost.getProviderMode() == ProviderMode.WIRELESS) {
            return true;
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
        wheelDirty = true;
    }

    private void invalidateEnergyStorageCache() {
        energyStorageCache.clear();
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
        if (inductionCardCacheDirty) {
            cachedInductionCardInstalled = computeInductionCardInstalled();
            inductionCardCacheDirty = false;
        }
        return cachedInductionCardInstalled;
    }

    private boolean computeInductionCardInstalled() {
        Item card = getAppliedFluxInductionCard();
        if (card == null) return false;
        if (this instanceof IUpgradeableObject upgradeableLogic) {
            return upgradeableLogic.getUpgrades().isInstalled(card);
        }
        return false;
    }

    private long simulateAvailableFluxEnergy(AEKey feKey, long maxAmount) {
        final long[] available = {0};
        gridNode.ifPresent((grid, node) -> available[0] =
                grid.getStorageService().getInventory()
                        .extract(feKey, maxAmount, Actionable.SIMULATE, wirelessSource));
        return available[0];
    }

    private long extractFluxEnergy(AEKey feKey, long amount) {
        if (amount <= 0) return 0;
        final long[] extracted = {0};
        gridNode.ifPresent((grid, node) -> extracted[0] =
                grid.getStorageService().getInventory()
                        .extract(feKey, amount, Actionable.MODULATE, wirelessSource));
        return extracted[0];
    }

    private void insertFluxEnergy(AEKey feKey, long amount) {
        if (amount <= 0) return;
        gridNode.ifPresent((grid, node) ->
                grid.getStorageService().getInventory()
                        .insert(feKey, amount, Actionable.MODULATE, wirelessSource));
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
        inductionCardCacheDirty = true;
        lastEnergyTickGameTime = -1;
        for (var slot : wheel) slot.clear();
        deferredMachines.clear();
        wheelPointer = 0;
        wheelDirty = true;
        returnRobinIndex = 0;
        lastReturnRobinTick = -1;
        lastSingleReturnTick = -1;
        lastReturnFlushTick = -1;
        returnBuffer.clear();
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
        inductionCardCacheDirty = true;
        lastEnergyTickGameTime = -1;
        wheelPointer = 0;
        wheelDirty = true;
        returnRobinIndex = 0;
        lastReturnRobinTick = -1;
        lastSingleReturnTick = -1;
        lastReturnFlushTick = -1;
        returnBuffer.clear();
    }
}
