package com.qianchang.ae2lt_core.crafting.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Linear-time autocrafting planner (the "fast path").
 *
 * <p>Models crafting as a memoized DAG (each item once) and runs two topological passes plus an
 * exact leaf check, all in {@code O(items + edges)} and independent of the requested amount:
 *
 * <ol>
 *   <li><b>capacity</b> (leaves→target): how many of each item current stock can yield, used to pick
 *       a recipe by actual feasibility (e.g. prefer the iron recipe over the diamond one when only
 *       iron is in stock) — no scarcity metric needed.</li>
 *   <li><b>demand</b> (target→leaves): aggregate required amounts with {@code ceil} arithmetic;
 *       returned inputs (containers / in-pattern catalysts) cost one seed batch, not {@code ×times}.</li>
 *   <li><b>validation</b>: aggregated demand on each raw leaf vs stock → exact missing report.</li>
 * </ol>
 *
 * <p>Scope is deliberately narrow (matches AE2's vanilla semantics): feasibility under current stock
 * only. Anything outside the safe envelope — a cycle/recursion in the reachable graph — makes
 * {@link #plan} return {@link CraftPlan#unsupported()} so the caller falls back to AE2's simulator.
 */
public final class CraftPlanner {

    private CraftPlanner() {
    }

    public enum MissingMode {
        /** Report shortages at the deepest raw leaves (fully decomposed). */
        DEEP
    }

    public static <K> CraftPlan<K> plan(CraftGraph<K> graph, K target, long amount) {
        return plan(graph, target, amount, MissingMode.DEEP);
    }

    public static <K> CraftPlan<K> plan(CraftGraph<K> graph, K target, long amount, MissingMode mode) {
        if (amount <= 0) {
            return new CraftPlan<>(true, true, Map.of(), Map.of(), Map.of(), Map.of(), 0, false);
        }

        // 1) Reachable items from the target (deduped → DAG, not a tree).
        Set<K> items = new LinkedHashSet<>();
        items.add(target);
        Deque<K> bfs = new ArrayDeque<>();
        bfs.add(target);
        while (!bfs.isEmpty()) {
            K x = bfs.poll();
            for (CraftPattern<K> p : graph.patternsFor(x)) {
                for (CraftInput<K> in : p.inputs()) {
                    if (items.add(in.key())) {
                        bfs.add(in.key());
                    }
                }
            }
        }

        // 2) Kahn topological order on dependency edges x -> inputItem. A cycle (recursion) means
        //    the fast path is out of scope; bail so the caller uses AE2's simulator.
        Map<K, Set<K>> deps = new HashMap<>();
        Map<K, Integer> indeg = new HashMap<>();
        for (K x : items) {
            indeg.putIfAbsent(x, 0);
            Set<K> d = new LinkedHashSet<>();
            for (CraftPattern<K> p : graph.patternsFor(x)) {
                for (CraftInput<K> in : p.inputs()) {
                    d.add(in.key());
                }
            }
            deps.put(x, d);
            for (K dep : d) {
                indeg.merge(dep, 1, Integer::sum);
            }
        }
        List<K> order = new ArrayList<>(items.size());
        Deque<K> ready = new ArrayDeque<>();
        for (K x : items) {
            if (indeg.get(x) == 0) {
                ready.add(x);
            }
        }
        while (!ready.isEmpty()) {
            K x = ready.poll();
            order.add(x);
            for (K dep : deps.get(x)) {
                if (indeg.merge(dep, -1, Integer::sum) == 0) {
                    ready.add(dep);
                }
            }
        }
        if (order.size() != items.size()) {
            return CraftPlan.unsupported(); // cycle / recursion
        }

        // 3) Capacity pass: reverse topo order (inputs finalized before their consumers).
        Map<K, Long> capacity = new HashMap<>(items.size() * 2);
        for (int i = order.size() - 1; i >= 0; i--) {
            K x = order.get(i);
            long best = 0;
            for (CraftPattern<K> p : graph.patternsFor(x)) {
                best = Math.max(best, producibleVia(p, capacity));
                if (Sat.isSaturated(best)) {
                    break;
                }
            }
            capacity.put(x, Sat.add(graph.stock(x), best));
        }

        // 4) Demand pass: topo order (every item's demand fully aggregated before it is processed).
        Map<K, Long> need = new HashMap<>();
        Map<K, Long> stockLeft = new HashMap<>();
        Map<CraftPattern<K>, Long> firings = new IdentityHashMap<>();
        Map<K, Long> usedStock = new HashMap<>();
        Map<K, Long> missing = new HashMap<>();
        Map<K, Long> grossDemand = new HashMap<>();
        need.put(target, amount);
        int processed = 0;

        for (K x : order) {
            long d = need.getOrDefault(x, 0L);
            if (d <= 0) {
                continue;
            }
            processed++;
            grossDemand.put(x, d); // pre-extraction request amount (for byte accounting)

            long avail = stockLeft.computeIfAbsent(x, graph::stock);
            if (avail > 0) {
                long take = Math.min(d, avail);
                stockLeft.put(x, avail - take);
                usedStock.merge(x, take, Sat::add);
                d -= take;
            }
            if (d <= 0) {
                continue;
            }

            List<CraftPattern<K>> patterns = graph.patternsFor(x);
            if (patterns.isEmpty()) {
                missing.merge(x, d, Sat::add); // raw leaf, not enough stock
                continue;
            }

            CraftPattern<K> chosen = selectPattern(patterns, capacity, d);
            long times = Sat.ceilDiv(d, chosen.outputAmount());
            firings.merge(chosen, times, Sat::add);
            for (CraftInput<K> in : chosen.inputs()) {
                long add = in.unitsFor(times);
                need.merge(in.key(), add, Sat::add);
            }
        }

        boolean feasible = missing.isEmpty();
        return new CraftPlan<>(true, feasible, firings, usedStock, missing, grossDemand, processed, false);
    }

    /**
     * First pattern (in preference order) whose current stock-derived capacity can cover {@code d};
     * otherwise the one with the highest capacity (best effort). Never null when {@code patterns} is
     * non-empty, so demand still propagates to the real missing leaves.
     */
    private static <K> CraftPattern<K> selectPattern(List<CraftPattern<K>> patterns,
                                                     Map<K, Long> capacity, long d) {
        CraftPattern<K> best = patterns.get(0);
        long bestCap = -1;
        for (CraftPattern<K> p : patterns) {
            long pv = producibleVia(p, capacity);
            if (pv >= d) {
                return p;
            }
            if (pv > bestCap) {
                bestCap = pv;
                best = p;
            }
        }
        return best;
    }

    /** How many of {@code p}'s output current capacities can yield in one batch. */
    private static <K> long producibleVia(CraftPattern<K> p, Map<K, Long> capacity) {
        long bound = Sat.SAT;
        for (CraftInput<K> in : p.inputs()) {
            long cap = capacity.getOrDefault(in.key(), 0L);
            bound = Math.min(bound, in.firingsFrom(cap)); // finite-use tools bound by uses·units
            if (bound == 0) {
                return 0;
            }
        }
        long perBatch = bound;
        return Sat.mul(perBatch, p.outputAmount());
    }
}
