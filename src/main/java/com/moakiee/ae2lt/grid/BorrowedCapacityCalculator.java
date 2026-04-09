package com.moakiee.ae2lt.grid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

/**
 * Assigns channels to devices in the overloaded network using
 * <b>Dinic's max-flow</b>.
 * <p>
 * Flow network model:
 * <ul>
 *   <li><b>Sources</b>: overloaded controllers (cap={@code channelsPerController}),
 *       vanilla controller faces (cap={@code 32×factor}).</li>
 *   <li><b>Relays</b> (node-split): overloaded cables/controllers = ∞,
 *       dense cables = 32×f, normal cables = 8×f.</li>
 *   <li><b>Sinks</b>: {@code REQUIRE_CHANNEL} devices → super-sink T (cap=1).</li>
 * </ul>
 * After max-flow, a device has a channel iff its device→T edge carries flow=1.
 */
public final class BorrowedCapacityCalculator {

    private static final int INF = Integer.MAX_VALUE / 2;

    /**
     * Active flow data set by the current PathingCalculation for use by
     * {@code GridNode.finalizeChannels()} injection. Cleared after finalization.
     * Thread-safe: Minecraft world ticks are single-threaded.
     */
    public static volatile Reference2IntOpenHashMap<IGridNode> activeNodeFlow;
    public static volatile Set<IGridNode> activeNetworkNodes;

    /**
     * Result of a max-flow channel assignment.
     *
     * @param channelNodes  devices that were granted a channel (flow=1 on device→T)
     * @param networkNodes  all nodes discovered in the network (for usedChannels override)
     * @param nodeFlow      flow through each node's node-split edge
     *                      (= usedChannels for that cable/device); default 0 for missing keys
     */
    public record Result(Set<GridNode> channelNodes,
                         Set<IGridNode> networkNodes,
                         Reference2IntOpenHashMap<IGridNode> nodeFlow) {}

    private BorrowedCapacityCalculator() {}

    /**
     * Runs max-flow on the entire controller network.
     *
     * @return channel assignment result, or {@code null} if the channel mode
     *         is INFINITE (caller should fall through to vanilla logic)
     */
    public static Result assignChannels(IGrid grid, List<IGridNode> overloadedControllers) {
        var channelMode = grid.getPathingService().getChannelMode();
        if (channelMode == ChannelMode.INFINITE) return null;

        Set<IGridNode> network = discoverNetwork(grid, overloadedControllers);

        return solve(grid, overloadedControllers, network, channelMode);
    }

    /**
     * BFS from ALL controllers to discover the entire network.
     * <ul>
     *   <li>Overloaded controllers are added as relay nodes (BFS through them).</li>
     *   <li>Vanilla controller faces seed their adjacent cables into the BFS.</li>
     *   <li>REQUIRE_CHANNEL + CANNOT_CARRY devices are included as sinks
     *       but not expanded through.</li>
     * </ul>
     */
    private static Set<IGridNode> discoverNetwork(
            IGrid grid, List<IGridNode> overloadedControllers) {

        Set<IGridNode> nodes = new ReferenceOpenHashSet<>();
        Queue<IGridNode> q = new ArrayDeque<>();

        for (var oc : overloadedControllers) {
            nodes.add(oc);
            q.add(oc);
        }

        for (var vc : grid.getMachineNodes(ControllerBlockEntity.class)) {
            if (vc.getOwner() instanceof OverloadedControllerBlockEntity) continue;
            for (var c : vc.getConnections()) {
                if (!(c instanceof GridConnection gc)) continue;
                var other = gc.getOtherSide(vc);
                if (other.getOwner() instanceof ControllerBlockEntity) continue;
                if (nodes.contains(other)) continue;

                if (other instanceof GridNode gn && gn.hasFlag(GridFlags.CANNOT_CARRY)) {
                    if (gn.hasFlag(GridFlags.REQUIRE_CHANNEL)) nodes.add(other);
                    continue;
                }
                nodes.add(other);
                q.add(other);
            }
        }

        while (!q.isEmpty()) {
            var cur = q.poll();
            for (var c : cur.getConnections()) {
                if (!(c instanceof GridConnection gc)) continue;
                var other = gc.getOtherSide(cur);
                if (nodes.contains(other)) continue;

                if (other.getOwner() instanceof ControllerBlockEntity) {
                    if (other.getOwner() instanceof OverloadedControllerBlockEntity) {
                        nodes.add(other);
                        q.add(other);
                    }
                    continue;
                }

                if (other instanceof GridNode gn && gn.hasFlag(GridFlags.CANNOT_CARRY)) {
                    if (gn.hasFlag(GridFlags.REQUIRE_CHANNEL)) nodes.add(other);
                    continue;
                }

                nodes.add(other);
                q.add(other);
            }
        }
        return nodes;
    }

    // ── flow-network construction & solve ────────────────────────────

    private static Result solve(IGrid grid,
                                List<IGridNode> overloadedControllers,
                                Set<IGridNode> network,
                                ChannelMode mode) {

        Reference2IntOpenHashMap<IGridNode> idx = new Reference2IntOpenHashMap<>();
        idx.defaultReturnValue(-1);
        int i = 0;
        for (var n : network) idx.put(n, i++);

        int total = network.size();
        int S = 2 * total, T = 2 * total + 1;
        Dinic dinic = new Dinic(2 * total + 2);

        // 1) node-split: in → out, capacity = relay capacity
        //    Record the edge index of each node-split edge for flow readback.
        int[] splitEdge = new int[total];
        for (var n : network) {
            int ci = idx.getInt(n);
            int cap = nodeCap(n, mode);
            splitEdge[ci] = dinic.edgeCount();
            dinic.addEdge(2 * ci, 2 * ci + 1, cap);
        }

        // 2) connections between discovered nodes (bidirectional, ∞)
        Set<Object> seen = new ReferenceOpenHashSet<>();
        for (var n : network) {
            int ci = idx.getInt(n);
            for (var c : n.getConnections()) {
                if (!(c instanceof GridConnection gc)) continue;
                var other = gc.getOtherSide(n);
                int oi = idx.getInt(other);
                if (oi < 0 || !seen.add(gc)) continue;
                dinic.addEdge(2 * ci + 1, 2 * oi, INF);
                dinic.addEdge(2 * oi + 1, 2 * ci, INF);
            }
        }

        // 3) overloaded controller sources: S → OC_in
        int supply = OverloadedChannelOwnerHelper.supplyPerController(mode.getCableCapacityFactor());
        for (var oc : overloadedControllers) {
            int ci = idx.getInt(oc);
            dinic.addEdge(S, 2 * ci, supply);
        }

        // 4) vanilla controller face sources: S → cable_in
        int faceCap = 32 * mode.getCableCapacityFactor();
        for (var node : grid.getMachineNodes(ControllerBlockEntity.class)) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) continue;
            for (var c : node.getConnections()) {
                if (!(c instanceof GridConnection gc)) continue;
                var other = gc.getOtherSide(node);
                if (other.getOwner() instanceof ControllerBlockEntity) continue;
                int oi = idx.getInt(other);
                if (oi >= 0) dinic.addEdge(S, 2 * oi, faceCap);
            }
        }

        // 5) REQUIRE_CHANNEL devices → T, cap=1
        List<IGridNode> sinkNodes = new ArrayList<>();
        List<Integer> sinkEdgeIndices = new ArrayList<>();
        for (var n : network) {
            if (!(n instanceof GridNode gn)) continue;
            if (!gn.hasFlag(GridFlags.REQUIRE_CHANNEL)) continue;
            int ci = idx.getInt(n);
            sinkEdgeIndices.add(dinic.edgeCount());
            dinic.addEdge(2 * ci + 1, T, 1);
            sinkNodes.add(n);
        }

        int maxFlow = dinic.maxFlow(S, T);

        // Collect winning devices
        Set<GridNode> winners = new ReferenceOpenHashSet<>();
        for (int j = 0; j < sinkNodes.size(); j++) {
            int edgeIdx = sinkEdgeIndices.get(j);
            if (dinic.residual(edgeIdx) == 0) {
                winners.add((GridNode) sinkNodes.get(j));
            }
        }

        // Collect flow through each node-split (= usedChannels for that node)
        Reference2IntOpenHashMap<IGridNode> nodeFlow = new Reference2IntOpenHashMap<>();
        nodeFlow.defaultReturnValue(0);
        for (var n : network) {
            int ci = idx.getInt(n);
            int originalCap = nodeCap(n, mode);
            int flowThrough = originalCap - dinic.residual(splitEdge[ci]);
            if (flowThrough > 0) {
                nodeFlow.put(n, flowThrough);
            }
        }

        return new Result(winners, network, nodeFlow);
    }

    /**
     * Relay capacity for flow-network node-split.
     * REQUIRE_CHANNEL + CANNOT_CARRY devices get cap=1 (consume one channel).
     */
    static int nodeCap(IGridNode node, ChannelMode mode) {
        if (OverloadedChannelOwnerHelper.is128ChannelOwner(node.getOwner())) return INF;
        int f = mode.getCableCapacityFactor();
        if (node instanceof GridNode gn) {
            if (gn.hasFlag(GridFlags.CANNOT_CARRY)) {
                return gn.hasFlag(GridFlags.REQUIRE_CHANNEL) ? 1 : 0;
            }
            if (gn.hasFlag(GridFlags.DENSE_CAPACITY)) return 32 * f;
        }
        return 8 * f;
    }

    // ── Dinic's max-flow ─────────────────────────────────────────────

    private static final class Dinic {
        private final int size;
        private final int[] head;
        private int[] to, cap, nxt;
        private int cnt;
        private final int[] level, cur;

        Dinic(int n) {
            size = n;
            head = new int[n];
            Arrays.fill(head, -1);
            int init = Math.max(n * 6, 64);
            to = new int[init];
            cap = new int[init];
            nxt = new int[init];
            level = new int[n];
            cur = new int[n];
        }

        int edgeCount() {
            return cnt;
        }

        int residual(int edgeIdx) {
            return cap[edgeIdx];
        }

        void addEdge(int u, int v, int c) {
            grow(cnt + 2);
            link(u, v, c);
            link(v, u, 0);
        }

        private void link(int u, int v, int c) {
            to[cnt] = v;
            cap[cnt] = c;
            nxt[cnt] = head[u];
            head[u] = cnt++;
        }

        private void grow(int need) {
            if (need <= to.length) return;
            int len = Math.max(to.length * 2, need);
            to = Arrays.copyOf(to, len);
            cap = Arrays.copyOf(cap, len);
            nxt = Arrays.copyOf(nxt, len);
        }

        private boolean bfs(int s, int t) {
            Arrays.fill(level, -1);
            level[s] = 0;
            Queue<Integer> q = new ArrayDeque<>();
            q.add(s);
            while (!q.isEmpty()) {
                int v = q.poll();
                for (int e = head[v]; e != -1; e = nxt[e]) {
                    if (cap[e] > 0 && level[to[e]] < 0) {
                        level[to[e]] = level[v] + 1;
                        q.add(to[e]);
                    }
                }
            }
            return level[t] >= 0;
        }

        private int dfs(int v, int t, int pushed) {
            if (v == t) return pushed;
            for (; cur[v] != -1; cur[v] = nxt[cur[v]]) {
                int e = cur[v];
                if (cap[e] > 0 && level[to[e]] == level[v] + 1) {
                    int d = dfs(to[e], t, Math.min(pushed, cap[e]));
                    if (d > 0) {
                        cap[e] -= d;
                        cap[e ^ 1] += d;
                        return d;
                    }
                }
            }
            return 0;
        }

        int maxFlow(int s, int t) {
            int flow = 0;
            while (bfs(s, t)) {
                System.arraycopy(head, 0, cur, 0, size);
                for (int d; (d = dfs(s, t, INF)) > 0; ) flow += d;
            }
            return flow;
        }
    }
}
