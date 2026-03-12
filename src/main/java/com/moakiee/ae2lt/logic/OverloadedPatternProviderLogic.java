package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.helpers.MachineSource;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessStrategy;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;

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
    private final appeng.api.networking.security.IActionSource wirelessSource;

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

    // ---- auto-return state (per-machine exponential backoff) --------------------

    /** Per-machine: game-tick at which the next poll is allowed. */
    private final Map<String, Long> machineNextPoll = new HashMap<>();

    /** Per-machine: current backoff interval in ticks. */
    private final Map<String, Integer> machineBackoff = new HashMap<>();

    /** Minimum polling interval (reset value after a successful extraction). */
    private static final int BACKOFF_MIN = 10;    // 0.5 second

    /** Maximum polling interval (cap for exponential growth). */
    private static final int BACKOFF_MAX = 1200;  // 60 seconds

    // ---- construction -----------------------------------------------------------

    public OverloadedPatternProviderLogic(IManagedGridNode mainNode,
                                          OverloadedPatternProviderBlockEntity host,
                                          int patternInventorySize) {
        super(mainNode, host, patternInventorySize);
        this.overloadedHost = host;
        this.gridNode = mainNode;
        this.wirelessSource = new MachineSource(mainNode::getNode);
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

        // 3. Clean invalid connections & collect valid targets
        overloadedHost.clearInvalidConnections();
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

        // Reset backoff for this machine so auto-return checks it promptly
        if (overloadedHost.isAutoReturn()) {
            var lvl = overloadedHost.getLevel();
            if (lvl instanceof ServerLevel sl) {
                var mkey = machineKey(sl.getServer().getLevel(conn.dimension()),
                        conn.pos(), conn.boundFace());
                machineBackoff.put(mkey, BACKOFF_MIN);
                machineNextPoll.put(mkey, sl.getGameTime() + BACKOFF_MIN);
            }
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
     * Items that cannot be inserted are dropped as entity items.
     */
    private void returnOverflowToNetwork() {
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
        // Anything left after network insert → drop as items
        if (!wirelessSendList.isEmpty()) {
            for (var stack : wirelessSendList) {
                stack.what().addDrops(stack.amount(), new ArrayList<>(),
                        overloadedHost.getLevel(), overloadedHost.getBlockPos());
            }
            wirelessSendList.clear();
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
        if (!overloadedHost.isAutoReturn()) return;
        if (!gridNode.isActive()) return;

        // Build the set of allowed output keys from all loaded patterns
        var allowedOutputs = collectPatternOutputKeys();
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
     * Collect all output AEKeys from every pattern loaded in this provider.
     */
    private Set<AEKey> collectPatternOutputKeys() {
        var keys = new HashSet<AEKey>();
        for (var pattern : getAvailablePatterns()) {
            for (var output : pattern.getOutputs()) {
                keys.add(output.what());
            }
        }
        return keys;
    }

    private void autoReturnNormal(ServerLevel level, Set<AEKey> allowedOutputs, long gameTick) {
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

    private void autoReturnWireless(ServerLevel sl, Set<AEKey> allowedOutputs, long gameTick) {
        var server = sl.getServer();
        for (var conn : overloadedHost.getConnections()) {
            var targetLevel = server.getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) continue;

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

    /** Unified machine key: "dim|x,y,z|face". */
    private static String machineKey(ServerLevel level, net.minecraft.core.BlockPos pos,
                                     net.minecraft.core.Direction face) {
        return level.dimension().location() + "|" + pos.asLong() + "|" + face.ordinal();
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
    private void updateBackoff(String key, long gameTick, boolean foundItems) {
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
        machineNextPoll.clear();
        machineBackoff.clear();
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
    }
}
