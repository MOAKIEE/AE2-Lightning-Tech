package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

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
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.api.storage.AEKeySlotFilter;
import appeng.util.inv.filter.IAEItemFilter;

import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
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

    private final UnlimitedReturnInventory unlimitedReturnInv;
    /** Full return inventory with totalPages * 9 slots; same as unlimitedReturnInv when pages == 1. */
    private final UnlimitedReturnInventory fullReturnInv;
    /** 9-slot return page view exposed via getReturnInv() for the GUI. */
    private final UnlimitedReturnInventory returnPageView;
    private boolean returnSyncing = false;

    private final OverloadedPatternProviderBlockEntity overloadedHost;
    private final IManagedGridNode gridNode;
    private final IActionSource wirelessSource;
    private final int totalCapacity;

    /** Currently displayed page index (0-based). */
    private int currentPage = 0;

    /** Set by readFromNBT when Level is not yet available; consumed by onReady(). */
    private boolean needsSavedDataLoad = false;

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

    // ---- push priority queue (O(log n) load balancing) ----------------------------

    private record PushEntry(WirelessConnection conn, int snapshotCount) {}

    private final PriorityQueue<PushEntry> pushPQ = new PriorityQueue<>(
            Comparator.comparingInt(PushEntry::snapshotCount));

    /** Reference to the valid-connections snapshot the PQ was built from. */
    private List<WirelessConnection> pushPQValidRef = List.of();

    private enum PushOutcome { SUCCESS, SOFT_FAIL, HARD_FAIL }

    // ---- PatternProviderTarget cache (avoids recreating strategies every push) -----

    private record TargetCacheKey(ResourceKey<Level> dimension, long posLong, Direction face) {}

    private static final class TargetCacheEntry {
        final WeakReference<BlockEntity> beRef;
        final PatternProviderTarget target;
        final long createdTick;

        TargetCacheEntry(BlockEntity be, PatternProviderTarget target, long tick) {
            this.beRef = new WeakReference<>(be);
            this.target = target;
            this.createdTick = tick;
        }

        boolean isValid(BlockEntity currentBE, long currentTick) {
            return beRef.get() == currentBE && (currentTick - createdTick) < TARGET_CACHE_TTL;
        }
    }

    private static final int TARGET_CACHE_TTL = 20;
    private final Map<TargetCacheKey, TargetCacheEntry> targetCache = new HashMap<>();

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

    // ---- eject mode state --------------------------------------------------------

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
        super(mainNode, host, Math.min(patternInventorySize, 36));
        mainNode.addService(IGridTickable.class, new Ticker());
        this.overloadedHost = host;
        this.gridNode = mainNode;
        this.wirelessSource = new MachineSource(mainNode::getNode);
        this.totalCapacity = patternInventorySize;

        var accessor = (PatternProviderLogicAccessor) this;
        IAEItemFilter patternFilter = new IAEItemFilter() {
            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
                return PatternDetailsHelper.isEncodedPattern(stack);
            }
        };

        if (totalCapacity > 36) {
            var largeInv = new appeng.util.inv.AppEngInternalInventory(this, totalCapacity);
            largeInv.setFilter(patternFilter);
            accessor.setPatternInventory(largeInv);
        } else {
            accessor.getPatternInventory().setFilter(patternFilter);
        }

        Runnable returnListener = () -> {
            gridNode.ifPresent((grid, node) ->
                    grid.getTickManager().alertDevice(node));
            overloadedHost.saveChanges();
        };
        AEKeySlotFilter returnFilter = (slot, key) -> {
            if (!overloadedHost.isFilteredImport()) return true;
            var filter = getOrBuildOutputFilter();
            return !filter.isEmpty() && filter.matches(key);
        };

        int totalPages = (totalCapacity + 35) / 36;
        int fullReturnSlots = totalPages * 9;

        if (fullReturnSlots > 9) {
            this.fullReturnInv = UnlimitedReturnInventory.create(returnListener, returnFilter, fullReturnSlots);
        } else {
            this.fullReturnInv = UnlimitedReturnInventory.create(returnListener, returnFilter);
        }
        this.unlimitedReturnInv = this.fullReturnInv;

        this.returnPageView = UnlimitedReturnInventory.create(() -> {
            if (!returnSyncing) {
                syncReturnFullFromPageView();
                returnListener.run();
            }
        }, returnFilter);

        accessor.setReturnInv(this.fullReturnInv);
    }

    @Override
    public PatternProviderReturnInventory getReturnInv() {
        return returnPageView;
    }

    public PatternProviderReturnInventory getInternalReturnInv() {
        return fullReturnInv;
    }

    @Override
    public void onChangeInventory(appeng.util.inv.AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
    }

    // ---- page management --------------------------------------------------------

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return (totalCapacity + 35) / 36;
    }

    public void setCurrentPage(int page) {
        int maxPage = getTotalPages() - 1;
        page = Math.max(0, Math.min(page, maxPage));
        if (page == currentPage) return;
        syncReturnFullFromPageView();
        currentPage = page;
        syncReturnPageViewFromFull();
    }

    /** Copy 9 slots from fullReturnInv to returnPageView based on currentPage. */
    public void syncReturnPageViewFromFull() {
        returnSyncing = true;
        try {
            int offset = currentPage * 9;
            for (int i = 0; i < 9; i++) {
                int fullIdx = offset + i;
                if (fullIdx < fullReturnInv.size()) {
                    returnPageView.setStack(i, fullReturnInv.getStack(fullIdx));
                } else {
                    returnPageView.setStack(i, null);
                }
            }
        } finally {
            returnSyncing = false;
        }
    }

    /** Copy 9 slots from returnPageView back to fullReturnInv. */
    private void syncReturnFullFromPageView() {
        int offset = currentPage * 9;
        for (int i = 0; i < 9; i++) {
            int fullIdx = offset + i;
            if (fullIdx < fullReturnInv.size()) {
                fullReturnInv.setStack(fullIdx, returnPageView.getStack(i));
            }
        }
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
        refreshEjectRegistrations();

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
            if (AdvancedAECompat.isDirectional(patternDetails)) {
                boolean result = pushPatternDirectionally(patternDetails, inputHolder);
                if (result && overloadedHost.isAutoReturn()) {
                    resetBackoffAllTargets();
                }
                if (result) alertGridTick();
                return result;
            }
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
        if (!wirelessSendList.isEmpty()) return false;
        if (!gridNode.isActive()) return false;
        if (!getAvailablePatterns().contains(pattern)) return false;
        if (getCraftingLockedReason() != LockCraftingMode.NONE) return false;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return false;
        var server = sl.getServer();

        var valid = getOrRefreshValidConnections(sl, sl.getGameTime());
        if (valid.isEmpty()) return false;

        if (valid != pushPQValidRef) {
            rebuildPushPQ(valid);
        }

        var retried = new ArrayList<PushEntry>(pushPQ.size());
        boolean success = false;

        while (!pushPQ.isEmpty()) {
            var entry = pushPQ.poll();
            var outcome = tryPushToConnection(pattern, inputs, entry.conn(), server);
            switch (outcome) {
                case SUCCESS -> {
                    int newCount = entry.snapshotCount() + 1;
                    distributionCounts.put(entry.conn(), newCount);
                    pushPQ.offer(new PushEntry(entry.conn(), newCount));
                    success = true;
                }
                case HARD_FAIL -> {
                    if (isConnectionAlive(entry.conn(), server)) {
                        retried.add(entry);
                    } else {
                        connectionsDirty = true;
                    }
                }
                case SOFT_FAIL -> retried.add(entry);
            }
            if (success) break;
        }

        for (var r : retried) pushPQ.offer(r);
        return success;
    }

    private static boolean isConnectionAlive(WirelessConnection conn,
                                             net.minecraft.server.MinecraftServer server) {
        var level = server.getLevel(conn.dimension());
        return level != null && level.isLoaded(conn.pos())
                && level.getBlockEntity(conn.pos()) != null;
    }

    private void rebuildPushPQ(List<WirelessConnection> valid) {
        pushPQ.clear();
        if (distributionCounts.size() > valid.size() * 2) {
            distributionCounts.keySet().retainAll(new HashSet<>(valid));
        }
        for (var conn : valid) {
            pushPQ.offer(new PushEntry(conn, distributionCounts.getOrDefault(conn, 0)));
        }
        pushPQValidRef = valid;
    }

    private PushOutcome tryPushToConnection(IPatternDetails pattern, KeyCounter[] inputs,
            WirelessConnection conn, net.minecraft.server.MinecraftServer server) {
        if (AdvancedAECompat.isDirectional(pattern)) {
            return tryPushToConnectionDirectionally(pattern, inputs, conn, server);
        }

        var targetLevel = server.getLevel(conn.dimension());
        if (targetLevel == null) return PushOutcome.HARD_FAIL;

        autoReturnBeforePush(targetLevel, conn);

        var adapter = MachineAdapterRegistry.find(targetLevel, conn.pos());
        if (adapter == null) return PushOutcome.HARD_FAIL;

        boolean blocking = isBlocking();
        var patternInputs = ((PatternProviderLogicAccessor) this).getPatternInputs();
        var result = adapter.pushCopies(
                targetLevel, conn.pos(), conn.boundFace(),
                pattern, inputs, 1,
                blocking, patternInputs, wirelessSource);
        if (result.acceptedCopies() == 0) return PushOutcome.SOFT_FAIL;

        if (!result.overflow().isEmpty()) {
            wirelessSendList.addAll(result.overflow());
            wirelessSendConn = conn;
        }

        ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(pattern);
        alertGridTick();

        if (overloadedHost.isAutoReturn()) {
            var mkey = machineKey(targetLevel, conn.pos(), conn.boundFace());
            machineBackoff.put(mkey, BACKOFF_MIN);
            machineNextPoll.put(mkey, targetLevel.getGameTime() + BACKOFF_MIN);
        }
        return PushOutcome.SUCCESS;
    }

    // ---- AdvancedAE directional push (NORMAL mode) --------------------------------

    /**
     * Push a directional AdvancedAE pattern through adjacent machines in NORMAL mode.
     * Each input key is routed to the target-machine face specified by the pattern's
     * directionMap; keys without a mapping use the default face (pushDir.getOpposite()).
     */
    private boolean pushPatternDirectionally(IPatternDetails pattern, KeyCounter[] inputs) {
        var accessor = (PatternProviderLogicAccessor) this;
        if (!accessor.getSendList().isEmpty()) return false;
        if (!gridNode.isActive()) return false;
        if (!getAvailablePatterns().contains(pattern)) return false;
        if (getCraftingLockedReason() != LockCraftingMode.NONE) return false;
        if (!pattern.supportsPushInputsToExternalInventory()) return false;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return false;

        var targets = overloadedHost.getTargets();
        if (targets.isEmpty()) return false;

        var providerPos = overloadedHost.getBlockPos();
        var patternInputKeys = accessor.getPatternInputs();

        EjectModeRegistry.setBypass(true);
        try {
            for (var pushDir : targets) {
                var targetPos = providerPos.relative(pushDir);
                var defaultFace = pushDir.getOpposite();
                var be = sl.getBlockEntity(targetPos);
                if (be == null) continue;

                var faceToTarget = buildDirectionalTargets(
                        sl, targetPos, be, defaultFace, pattern, inputs, wirelessSource);
                if (faceToTarget == null) continue;

                if (isBlocking()) {
                    var anyTarget = faceToTarget.values().iterator().next();
                    if (anyTarget.containsPatternInput(patternInputKeys)) continue;
                }

                if (!simulateDirectionalAcceptance(faceToTarget, defaultFace, pattern, inputs)) continue;

                commitDirectionalPush(pattern, inputs, faceToTarget, defaultFace);

                accessor.setSendDirection(defaultFace);
                accessor.invokeSendStacksOut();
                accessor.invokeOnPushPatternSuccess(pattern);
                return true;
            }
            return false;
        } finally {
            EjectModeRegistry.setBypass(false);
        }
    }

    // ---- AdvancedAE directional push (WIRELESS mode) -----------------------------

    /**
     * Push a directional AdvancedAE pattern to a wireless target.
     * Behaves as if the provider were physically placed on {@code conn.boundFace()}.
     * Each input key is routed to the target-machine face from the directionMap;
     * keys without a mapping default to {@code conn.boundFace()}.
     */
    private PushOutcome tryPushToConnectionDirectionally(IPatternDetails pattern, KeyCounter[] inputs,
            WirelessConnection conn, net.minecraft.server.MinecraftServer server) {
        var targetLevel = server.getLevel(conn.dimension());
        if (targetLevel == null) return PushOutcome.HARD_FAIL;
        if (!targetLevel.isLoaded(conn.pos())) return PushOutcome.HARD_FAIL;
        if (!pattern.supportsPushInputsToExternalInventory()) return PushOutcome.SOFT_FAIL;

        autoReturnBeforePush(targetLevel, conn);

        var be = targetLevel.getBlockEntity(conn.pos());
        if (be == null) return PushOutcome.HARD_FAIL;

        var defaultFace = conn.boundFace();

        EjectModeRegistry.setBypass(true);
        try {
            var faceToTarget = buildDirectionalTargets(
                    targetLevel, conn.pos(), be, defaultFace, pattern, inputs, wirelessSource);
            if (faceToTarget == null) return PushOutcome.SOFT_FAIL;

            if (isBlocking()) {
                var patternInputKeys = ((PatternProviderLogicAccessor) this).getPatternInputs();
                var anyTarget = faceToTarget.values().iterator().next();
                if (anyTarget.containsPatternInput(patternInputKeys)) return PushOutcome.SOFT_FAIL;
            }

            if (!simulateDirectionalAcceptance(faceToTarget, defaultFace, pattern, inputs))
                return PushOutcome.SOFT_FAIL;

            var overflow = commitDirectionalPushWithOverflow(pattern, inputs, faceToTarget, defaultFace);
            if (!overflow.isEmpty()) {
                wirelessSendList.addAll(overflow);
                wirelessSendConn = conn;
            }
        } finally {
            EjectModeRegistry.setBypass(false);
        }

        ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(pattern);
        alertGridTick();

        if (overloadedHost.isAutoReturn()) {
            var mkey = machineKey(targetLevel, conn.pos(), conn.boundFace());
            machineBackoff.put(mkey, BACKOFF_MIN);
            machineNextPoll.put(mkey, targetLevel.getGameTime() + BACKOFF_MIN);
        }
        return PushOutcome.SUCCESS;
    }

    // ---- directional push helpers ------------------------------------------------

    @Nullable
    private PatternProviderTarget getCachedTarget(
            ServerLevel level, BlockPos pos, BlockEntity be, Direction face, IActionSource source) {
        long gameTick = level.getGameTime();
        var key = new TargetCacheKey(level.dimension(), pos.asLong(), face);
        var entry = targetCache.get(key);
        if (entry != null && entry.isValid(be, gameTick)) {
            return entry.target;
        }
        var target = PatternProviderTarget.get(level, pos, be, face, source);
        if (target != null) {
            targetCache.put(key, new TargetCacheEntry(be, target, gameTick));
        } else {
            targetCache.remove(key);
        }
        return target;
    }

    /**
     * Build a map of face -> PatternProviderTarget for all unique faces
     * referenced by the directional pattern's inputs.
     *
     * @return the map, or {@code null} if any required target cannot be resolved
     */
    @Nullable
    private Map<Direction, PatternProviderTarget> buildDirectionalTargets(
            ServerLevel level, BlockPos targetPos, BlockEntity be,
            Direction defaultFace, IPatternDetails pattern,
            KeyCounter[] inputs, IActionSource source) {
        var map = new HashMap<Direction, PatternProviderTarget>();
        for (var inputList : inputs) {
            for (var entry : inputList) {
                var dir = AdvancedAECompat.getDirectionForKey(pattern, entry.getKey());
                var face = dir != null ? dir : defaultFace;
                map.computeIfAbsent(face, f -> getCachedTarget(level, targetPos, be, f, source));
            }
        }
        if (map.containsValue(null)) return null;
        return map;
    }

    /**
     * Simulate whether all directional targets can accept their respective inputs.
     */
    private static boolean simulateDirectionalAcceptance(
            Map<Direction, PatternProviderTarget> faceToTarget,
            Direction defaultFace,
            IPatternDetails pattern, KeyCounter[] inputs) {
        for (var inputList : inputs) {
            for (var entry : inputList) {
                var dir = AdvancedAECompat.getDirectionForKey(pattern, entry.getKey());
                var face = dir != null ? dir : defaultFace;
                var target = faceToTarget.get(face);
                if (target == null) return false;
                if (target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Commit directional push for NORMAL mode.
     * Overflow goes to the parent's sendList via accessor.
     */
    private void commitDirectionalPush(IPatternDetails pattern, KeyCounter[] inputs,
            Map<Direction, PatternProviderTarget> faceToTarget, Direction defaultFace) {
        var accessor = (PatternProviderLogicAccessor) this;
        pattern.pushInputsToExternalInventory(inputs, (what, amount) -> {
            var dir = AdvancedAECompat.getDirectionForKey(pattern, what);
            var face = dir != null ? dir : defaultFace;
            var target = faceToTarget.get(face);
            if (target != null) {
                var inserted = target.insert(what, amount, Actionable.MODULATE);
                if (inserted < amount) {
                    accessor.invokeAddToSendList(what, amount - inserted);
                }
            } else {
                accessor.invokeAddToSendList(what, amount);
            }
        });
    }

    /**
     * Commit directional push for WIRELESS mode.
     * Returns overflow items directly instead of using the parent's sendList.
     */
    private static List<GenericStack> commitDirectionalPushWithOverflow(
            IPatternDetails pattern, KeyCounter[] inputs,
            Map<Direction, PatternProviderTarget> faceToTarget, Direction defaultFace) {
        var overflow = new ArrayList<GenericStack>();
        pattern.pushInputsToExternalInventory(inputs, (what, amount) -> {
            var dir = AdvancedAECompat.getDirectionForKey(pattern, what);
            var face = dir != null ? dir : defaultFace;
            var target = faceToTarget.get(face);
            if (target != null) {
                var inserted = target.insert(what, amount, Actionable.MODULATE);
                if (inserted < amount) {
                    overflow.add(new GenericStack(what, amount - inserted));
                }
            } else {
                overflow.add(new GenericStack(what, amount));
            }
        });
        return overflow;
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
        if (!hasAnyTickWork()) return;

        tickWirelessInductionEnergy();

        var returnMode = overloadedHost.getReturnMode();
        if (returnMode != ReturnMode.AUTO) return;
        if (!gridNode.isActive()) return;

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
        if (!fullReturnInv.isEmpty()) return true;
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
     * Uses AE2-level outputs (GenericStack) to handle both items and fluids,
     * cross-referencing with overload details for ID_ONLY match modes.
     */
    private AllowedOutputFilter collectPatternOutputFilter() {
        var filter = new AllowedOutputFilter();
        for (var pattern : getAvailablePatterns()) {
            if (pattern instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
                var ae2Outputs = pattern.getOutputs();
                var overloadOutputs = overloadDetails.overloadPatternDetailsView().outputs();
                int count = Math.min(ae2Outputs.size(), overloadOutputs.size());
                for (int i = 0; i < count; i++) {
                    var aeKey = ae2Outputs.get(i).what();
                    if (overloadOutputs.get(i).matchMode() == MatchMode.ID_ONLY) {
                        filter.allowIdOnly(aeKey);
                    } else {
                        filter.allowStrict(aeKey);
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
            insertOutputsToReturnInv(outputs);
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


    private void autoReturnBeforePush(ServerLevel sl, WirelessConnection conn) {
        if (overloadedHost.getReturnMode() != ReturnMode.AUTO) return;
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
        insertOutputsToReturnInv(outputs);
    }

    private void insertOutputsToReturnInv(List<GenericStack> outputs) {
        for (var stack : outputs) {
            fullReturnInv.insert(0, stack.what(), stack.amount(), Actionable.MODULATE);
        }
    }

    // ---- eject mode lifecycle ----------------------------------------------------

    /**
     * Rebuild eject-mode registrations based on the current return mode
     * and wireless connections. Should be called whenever return mode,
     * connections, or patterns change.
     */
    public void refreshEjectRegistrations() {
        var removed = EjectModeRegistry.unregisterAll(overloadedHost);
        invalidateCapabilitiesAt(removed);

        if (overloadedHost.getReturnMode() != ReturnMode.EJECT) return;
        if (overloadedHost.getProviderMode() != ProviderMode.WIRELESS) return;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;

        for (var conn : overloadedHost.getConnections()) {
            var targetLevel = sl.getServer().getLevel(conn.dimension());
            if (targetLevel == null) continue;

            var adjacentPos = conn.pos().relative(conn.boundFace());
            var queryFace = conn.boundFace().getOpposite();
            var ghostBE = new GhostOutputBlockEntity(adjacentPos);
            ghostBE.setLevel(targetLevel);

            var entry = new EjectModeRegistry.EjectEntry(
                    new java.lang.ref.WeakReference<>(overloadedHost),
                    ghostBE
            );

            EjectModeRegistry.register(targetLevel.dimension(), adjacentPos.asLong(), queryFace, entry);
            invalidateCapabilitiesAt(targetLevel, adjacentPos);
        }
    }

    private void invalidateCapabilitiesAt(List<EjectModeRegistry.DimPos> positions) {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        var server = sl.getServer();
        for (var dp : positions) {
            var targetLevel = server.getLevel(dp.dimension());
            if (targetLevel != null) {
                targetLevel.invalidateCapabilities(dp.pos());
            }
        }
    }

    private static void invalidateCapabilitiesAt(@Nullable ServerLevel level, BlockPos pos) {
        if (level != null) {
            level.invalidateCapabilities(pos);
        }
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
        refreshEjectRegistrations();
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
        pushPQValidRef = List.of();
        targetCache.clear();
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


    // ---- SavedData persistence helpers ------------------------------------------

    private void saveToSavedData() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        var inv = ((PatternProviderLogicAccessor) this).getPatternInventory();
        var patterns = new ItemStack[inv.size()];
        for (int i = 0; i < inv.size(); i++) {
            patterns[i] = inv.getStackInSlot(i);
        }
        PatternStorageSavedData.get(sl).set(overloadedHost.getBlockPos().asLong(), patterns);
    }

    private void loadFromSavedData() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").warn(
                    "[SavedData] loadFromSavedData skipped: level={} pos={}",
                    level, overloadedHost.getBlockPos());
            return;
        }
        var savedData = PatternStorageSavedData.get(sl);
        var stored = savedData.get(overloadedHost.getBlockPos().asLong());
        if (stored == null) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").info(
                    "[SavedData] No stored data for pos={}", overloadedHost.getBlockPos());
            return;
        }
        org.slf4j.LoggerFactory.getLogger("ae2lt").info(
                "[SavedData] Loaded {} patterns for pos={}", stored.length, overloadedHost.getBlockPos());
        var inv = ((PatternProviderLogicAccessor) this).getPatternInventory();
        int limit = Math.min(stored.length, inv.size());
        for (int i = 0; i < limit; i++) {
            inv.setItemDirect(i, stored[i] != null ? stored[i] : ItemStack.EMPTY);
        }
    }

    public void removeSavedData() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        PatternStorageSavedData.get(sl).remove(overloadedHost.getBlockPos().asLong());
    }

    /**
     * Called from {@code OverloadedPatternProviderBlockEntity.onReady()} when
     * Level is guaranteed to be available. Completes deferred SavedData loading
     * that was skipped during readFromNBT (where Level is still null).
     */
    public void onBlockEntityReady() {
        if (needsSavedDataLoad) {
            needsSavedDataLoad = false;
            loadFromSavedData();
        }
    }

    public int getTotalCapacity() {
        return totalCapacity;
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
        if (totalCapacity > 36) {
            removeSavedData();
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        if (totalCapacity > 36) {
            removeSavedData();
        }
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
        invalidateCapabilitiesAt(EjectModeRegistry.unregisterAll(overloadedHost));
    }

    // ---- NBT persistence --------------------------------------------------------

    private static final String TAG_W_SEND_LIST = "WirelessSendList";
    private static final String TAG_W_SEND_CONN = "WirelessSendConn";
    private static final String TAG_W_ROUND_ROBIN = "WirelessRoundRobin";

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        if (totalCapacity > 36) {
            var accessor = (PatternProviderLogicAccessor) this;
            var realInv = accessor.getPatternInventory();
            accessor.setPatternInventory(new appeng.util.inv.AppEngInternalInventory(this, totalCapacity));
            super.writeToNBT(tag, registries);
            accessor.setPatternInventory(realInv);
            saveToSavedData();
        } else {
            super.writeToNBT(tag, registries);
        }
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
        if (totalCapacity > 36) {
            needsSavedDataLoad = true;
        }
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
        refreshEjectRegistrations();
    }
}
