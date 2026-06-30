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
 * v2 autocrafting planner: dynamic-capacity greedy over a shared mutable pool, with byproduct reuse
 * and bounded backtracking for contended choices.
 *
 * <p>This is the evolution of {@link CraftPlanner} (v1, closed-form two-pass). It keeps v1's strengths
 * — quantity-independent batching ({@code ceil} arithmetic), {@code returned} (container/catalyst)
 * inputs in closed form, saturating arithmetic — and adds:
 *
 * <ul>
 *   <li><b>In-engine cycle breaking ("去头尾")</b>: instead of declining when the recipe graph has a
 *       cycle, a DFS from the target drops back-edges, keeping the recipe direction toward the target
 *       and cutting the reverse. A compress/decompress pair (1 block ⇄ 9 ingots) is planned directly;
 *       the reverse side resolves from stock/missing. Cuts only remove options, so feasibility is never
 *       overstated (no false positives).</li>
 *   <li><b>Shared pool + byproducts</b>: stock and crafting byproducts live in one mutable pool;
 *       demand draws byproducts first, then stock, then crafts. Multi-output patterns are supported
 *       (a pattern's extra outputs feed sibling demands).</li>
 *   <li><b>Dynamic-capacity greedy</b>: among an item's recipes, the one with the highest current
 *       capacity ({@code stock + craftable}) is preferred, so the planner naturally balances onto the
 *       recipe current stock can actually fulfill (no scarcity metric needed).</li>
 *   <li><b>Bounded backtracking</b>: contended items (more than one recipe) are searched in
 *       capacity order with a {@code trail} for commit/rollback and a <b>per-node visit cap K</b>.
 *       The cap bounds total work to {@code O(K · edges)} — never exponential, never N². When a node's
 *       budget is exhausted it freezes (best-effort commit on its top recipe), a safe degradation.</li>
 * </ul>
 *
 * <p><b>Soundness (no false positives):</b> the pool is never overdrawn (a draw is capped by what is
 * actually present), so a plan reports {@link CraftPlan#feasible() feasible} only when every demand was
 * met from stock or from a craft whose own inputs were met. Shortfalls always surface in
 * {@link CraftPlan#missing()}.
 */
public final class CraftPlannerV2<K> {

    /**
     * Default per-node visit cap. It is a safety net, not the workhorse: normal graphs are resolved by
     * the linear backbone or recover via a handful of backtracks, never approaching this bound. 64 is
     * generous enough that {@link CraftPlan#budgetExhausted()} stays false on realistic inputs while
     * still guaranteeing {@code O(64·E)} worst-case work on adversarial contention. Callers may tier it
     * (e.g. √n on an overloaded CPU).
     */
    public static final int DEFAULT_VISIT_CAP = 64;

    private final CraftGraph<K> graph;
    private final int visitCap;

    private final Map<K, List<CraftPattern<K>>> patternsByOutput = new HashMap<>();
    private Map<K, Long> capacity;

    // Mutable planning state (all writes go through the trail so a branch can be rolled back).
    private final Map<K, Long> bpPool = new HashMap<>();      // byproduct / surplus supply
    private final Map<K, Long> stockLeft = new HashMap<>();   // remaining inventory snapshot
    private final Map<K, Long> usedStock = new HashMap<>();   // drawn from inventory
    private final Map<K, Long> missing = new HashMap<>();     // unmet at raw leaves
    private final Map<K, Long> grossDemand = new HashMap<>(); // pre-extraction request totals (bytes)
    private final Map<CraftPattern<K>, Long> firings = new IdentityHashMap<>();

    // Monotonic (never rolled back) — this is what bounds the search.
    private final Map<K, Integer> visit = new HashMap<>();
    private final Deque<Runnable> trail = new ArrayDeque<>();
    private int processed;

    // Set once if any node hits the per-node visit cap and falls back to best-effort. Not rolled back.
    private boolean budgetExhausted;

    // Running sum of all unmet (missing) amounts; trail-restored. A search branch is accepted iff it
    // introduces no new missing, so the decision survives nested single-recipe commits.
    private long missingTotal;

    private CraftPlannerV2(CraftGraph<K> graph, int visitCap) {
        this.graph = graph;
        this.visitCap = Math.max(1, visitCap);
    }

    public static <K> CraftPlan<K> plan(CraftGraph<K> graph, K target, long amount) {
        return plan(graph, target, amount, DEFAULT_VISIT_CAP);
    }

    public static <K> CraftPlan<K> plan(CraftGraph<K> graph, K target, long amount, int visitCap) {
        return new CraftPlannerV2<>(graph, visitCap).run(target, amount);
    }

    private CraftPlan<K> run(K target, long amount) {
        if (amount <= 0) {
            return new CraftPlan<>(true, true, Map.of(), Map.of(), Map.of(), Map.of(), 0, false);
        }

        // Build an acyclic view of the reachable recipe graph: a DFS from the target drops any recipe
        // whose input is an ancestor still being expanded (a back-edge), i.e. AE2's "去头尾". For a
        // compress/decompress pair (1 block ⇄ 9 ingots) this keeps the direction toward the target
        // (compress when making blocks, decompress when making ingots) and cuts the reverse, so the
        // other side falls back to stock/missing — exactly what a sane plan does. No cycle ever reaches
        // the topological passes, so we never bail to AE2 just because a reverse recipe exists.
        Set<K> items = new LinkedHashSet<>();
        List<K> postOrder = new ArrayList<>();
        buildDag(target, postOrder, items);
        List<K> order = new ArrayList<>(postOrder.size()); // target-first topo order = reverse post-order
        for (int i = postOrder.size() - 1; i >= 0; i--) {
            order.add(postOrder.get(i));
        }
        this.capacity = capacityFromOrder(order, items.size());

        // 1) Linear backbone (v2-memo-deps / v2-lazy-deduct): one topological aggregation pass,
        //    each item resolved exactly once = O(n + E). Reservation-based capacity gives O(1)
        //    deduction (no recompute loop). When this fully succeeds, contention never mattered.
        CraftPlan<K> linear = linearPass(order, target, amount);
        if (linear.feasible()) {
            return linear;
        }

        // 2) Contended cone only: fall back to the bounded recursive search (trail + per-node cap K).
        for (K x : items) {
            stockLeft.put(x, graph.stock(x));
        }
        obtain(target, amount);
        boolean feasible = missing.isEmpty();
        return new CraftPlan<>(true, feasible,
                new IdentityHashMap<>(firings),
                new HashMap<>(usedStock),
                new HashMap<>(missing),
                new HashMap<>(grossDemand),
                processed,
                budgetExhausted);
    }

    // ---- graph construction ------------------------------------------------

    private static final int GRAY = 1; // on the current DFS path (an ancestor)
    private static final int BLACK = 2; // fully expanded

    private static final class Frame<K> {
        final K node;
        final List<K> children;
        int i;

        Frame(K node, List<K> children) {
            this.node = node;
            this.children = children;
        }
    }

    /**
     * Iterative DFS from {@code target} that records each node's <em>acyclic</em> recipe set into
     * {@link #patternsByOutput} and emits a post-order. A recipe is kept only if none of its inputs is an
     * ancestor currently on the DFS path ({@code GRAY}); such a recipe would close a cycle ("去头尾"), so
     * it is dropped and the input is satisfied from stock/another recipe instead. Each node and edge is
     * touched once → {@code O(n + E)}; iterative (not recursive) so deep graphs can't overflow the stack.
     */
    private void buildDag(K target, List<K> postOrderOut, Set<K> itemsOut) {
        Map<K, Integer> color = new HashMap<>();
        Deque<Frame<K>> stack = new ArrayDeque<>();
        color.put(target, GRAY);
        itemsOut.add(target);
        stack.push(frameFor(target, color, itemsOut));
        while (!stack.isEmpty()) {
            Frame<K> f = stack.peek();
            if (f.i < f.children.size()) {
                K c = f.children.get(f.i++);
                if (color.get(c) == null) { // WHITE -> descend (GRAY children are excluded by frameFor)
                    color.put(c, GRAY);
                    stack.push(frameFor(c, color, itemsOut));
                }
            } else {
                color.put(f.node, BLACK);
                postOrderOut.add(f.node);
                stack.pop();
            }
        }
    }

    private Frame<K> frameFor(K x, Map<K, Integer> color, Set<K> itemsOut) {
        List<CraftPattern<K>> all = graph.patternsFor(x);
        List<CraftPattern<K>> usable = new ArrayList<>(all.size());
        Set<K> children = new LinkedHashSet<>();
        for (CraftPattern<K> p : all) {
            boolean cyclic = false;
            for (CraftInput<K> in : p.inputs()) {
                Integer col = color.get(in.key());
                if (col != null && col == GRAY) { // input is an ancestor being made -> back-edge, cut it
                    cyclic = true;
                    break;
                }
            }
            if (cyclic) {
                continue;
            }
            usable.add(p);
            for (CraftInput<K> in : p.inputs()) {
                children.add(in.key());
                itemsOut.add(in.key());
            }
        }
        patternsByOutput.put(x, usable);
        return new Frame<>(x, new ArrayList<>(children));
    }

    /** cap[X] = stock + max recipe-producible (reverse-topo, byproducts ignored = optimistic upper bound). */
    private Map<K, Long> capacityFromOrder(List<K> order, int sizeHint) {
        Map<K, Long> cap = new HashMap<>(sizeHint * 2);
        for (int i = order.size() - 1; i >= 0; i--) {
            K x = order.get(i);
            long best = 0;
            for (CraftPattern<K> p : patternsByOutput.getOrDefault(x, List.of())) {
                best = Math.max(best, producibleVia(p, cap));
                if (Sat.isSaturated(best)) {
                    break;
                }
            }
            cap.put(x, Sat.add(graph.stock(x), best));
        }
        return cap;
    }

    private long producibleVia(CraftPattern<K> p, Map<K, Long> cap) {
        long bound = Sat.SAT;
        for (CraftInput<K> in : p.inputs()) {
            long c = cap.getOrDefault(in.key(), 0L);
            bound = Math.min(bound, in.firingsFrom(c)); // finite-use tools bound by uses·units
            if (bound == 0) {
                return 0;
            }
        }
        return Sat.mul(bound, p.outputAmount());
    }

    // ---- linear backbone: one topological aggregation pass, each item resolved once -------------

    /**
     * Resolves the whole request in a single topological pass (target → leaves). Each item is visited
     * once, its full demand already aggregated, then split across recipes by current remaining
     * capacity. Capacity is reserved with O(1) deduction ({@code need} doubles as the reservation
     * counter), so there is no recompute loop — this is the {@code O(n + E)} clean backbone. If it
     * comes back feasible the plan is exact and contention never bound; otherwise the caller escalates
     * to the bounded search on the contended cone.
     */
    private CraftPlan<K> linearPass(List<K> order, K target, long amount) {
        Map<K, Long> need = new HashMap<>();
        Map<K, Long> bp = new HashMap<>();       // byproduct / surplus pool
        Map<K, Long> stockL = new HashMap<>();   // remaining inventory
        Map<K, Long> used = new HashMap<>();
        Map<K, Long> miss = new HashMap<>();
        Map<K, Long> gross = new HashMap<>();
        Map<CraftPattern<K>, Long> fired = new IdentityHashMap<>();
        need.put(target, amount);
        int done = 0;

        for (K x : order) {
            long d = need.getOrDefault(x, 0L);
            if (d <= 0) {
                continue;
            }
            done++;
            gross.put(x, d);

            long fromBp = Math.min(d, lget(bp, x));
            if (fromBp > 0) {
                bp.put(x, lget(bp, x) - fromBp);
                d -= fromBp;
            }
            long fromStock = Math.min(d, stockL.computeIfAbsent(x, graph::stock));
            if (fromStock > 0) {
                stockL.put(x, lget(stockL, x) - fromStock);
                used.merge(x, fromStock, Sat::add);
                d -= fromStock;
            }
            if (d <= 0) {
                continue;
            }

            List<CraftPattern<K>> ps = patternsByOutput.getOrDefault(x, List.of());
            if (ps.isEmpty()) {
                miss.merge(x, d, Sat::add);
                continue;
            }
            allocateLinear(x, d, ps, need, bp, fired);
        }

        boolean feasible = miss.isEmpty();
        return new CraftPlan<>(true, feasible, fired, used, miss, gross, done, false);
    }

    /** Split {@code d} of {@code x} across recipes by current remaining capacity (dynamic balance). */
    private void allocateLinear(K x, long d, List<CraftPattern<K>> ps,
                                Map<K, Long> need, Map<K, Long> bp, Map<CraftPattern<K>, Long> fired) {
        List<CraftPattern<K>> ordered = new ArrayList<>(ps);
        ordered.sort((a, b) -> Long.compare(capRemainingVia(b, need), capRemainingVia(a, need)));

        for (CraftPattern<K> r : ordered) {
            if (d <= 0) {
                break;
            }
            long p = capRemainingVia(r, need);
            if (p <= 0) {
                continue;
            }
            long make = Math.min(d, p);
            long t = Sat.ceilDiv(make, r.outputAmount());
            long consumed = Math.min(d, Sat.mul(t, r.outputAmount()));
            fireLinear(x, r, t, consumed, need, bp, fired);
            d -= consumed;
        }
        // Leftover nobody had capacity for: push demand down the primary recipe; the deficit surfaces
        // at the raw leaves (same optimistic behaviour as AE2's simulation).
        if (d > 0) {
            CraftPattern<K> r0 = ps.get(0);
            long t = Sat.ceilDiv(d, r0.outputAmount());
            fireLinear(x, r0, t, d, need, bp, fired);
        }
    }

    private void fireLinear(K x, CraftPattern<K> r, long t, long consumedOwn,
                            Map<K, Long> need, Map<K, Long> bp, Map<CraftPattern<K>, Long> fired) {
        fired.merge(r, t, Sat::add);
        for (CraftInput<K> in : r.inputs()) {
            long amt = in.unitsFor(t); // closed form: normal=amount·t, catalyst=amount, finite-use=amount·ceil(t/uses)
            need.merge(in.key(), amt, Sat::add); // demand forward = reservation (capRemaining shrinks)
        }
        for (CraftOutput<K> out : r.byproducts()) {
            bp.merge(out.key(), Sat.mul(out.amount(), t), Sat::add);
        }
        long surplus = Sat.mul(t, r.outputAmount()) - consumedOwn;
        if (surplus > 0) {
            bp.merge(x, surplus, Sat::add);
        }
    }

    /** capRemaining(input) = static capacity − already-reserved demand; combined over a recipe's inputs. */
    private long capRemainingVia(CraftPattern<K> r, Map<K, Long> need) {
        long bound = Sat.SAT;
        for (CraftInput<K> in : r.inputs()) {
            long cr = Math.max(0L, capacity.getOrDefault(in.key(), 0L) - need.getOrDefault(in.key(), 0L));
            bound = Math.min(bound, in.firingsFrom(cr));
            if (bound == 0) {
                return 0;
            }
        }
        return Sat.mul(bound, r.outputAmount());
    }

    private long lget(Map<K, Long> m, K k) {
        Long v = m.get(k);
        return v == null ? 0L : v;
    }

    // ---- core: obtain d units of x, consuming from pool/stock, crafting the rest ----------------

    /** @return the amount of {@code x} that could NOT be obtained (also recorded in {@link #missing}). */
    private long obtain(K x, long d) {
        if (d <= 0) {
            return 0;
        }
        bump(grossDemand, x, d);
        d -= drawPools(x, d);
        if (d <= 0) {
            return 0;
        }

        List<CraftPattern<K>> ps = patternsByOutput.getOrDefault(x, List.of());
        if (ps.isEmpty()) {
            addMissing(x, d);
            return d;
        }

        processed++;
        int v = visit.getOrDefault(x, 0);
        visit.put(x, v + 1);

        // Single recipe, or budget exhausted: no search — best-effort commit (deficits surface as
        // missing at the leaves). Freezing on exhaustion is the safe degradation.
        if (ps.size() == 1 || v >= visitCap) {
            if (v >= visitCap) {
                budgetExhausted = true; // the per-node cap, not a clean decision, ended the search here
            }
            return commitBestEffort(ps, x, d);
        }

        List<CraftPattern<K>> ordered = byCapacityDesc(ps);
        for (CraftPattern<K> r : ordered) {
            int mark = trail.size();
            long beforeMissing = missingTotal;
            long unmet = fire(x, r, d, true);
            if (missingTotal == beforeMissing) {
                return unmet; // this recipe satisfied d without introducing any shortfall
            }
            rollback(mark); // restores pool/firings/missing(+total); try the next recipe
        }
        // No recipe fully worked within budget: commit the highest-capacity one (records missing).
        return commitBestEffort(ordered, x, d);
    }

    private long commitBestEffort(List<CraftPattern<K>> ps, K x, long d) {
        CraftPattern<K> r = ps.size() == 1 ? ps.get(0) : byCapacityDesc(ps).get(0);
        return fire(x, r, d, false);
    }

    /**
     * Fire {@code r} enough times to make {@code d} of {@code x}, obtaining its inputs recursively and
     * injecting outputs (surplus + byproducts) into the pool.
     *
     * @param search if true, abort accounting is meaningful: returns total input shortfall so the
     *               caller can decide to roll back and try another recipe.
     * @return input shortfall (0 means this recipe fully satisfied d).
     */
    private long fire(K x, CraftPattern<K> r, long d, boolean search) {
        long entryMissing = missingTotal;
        long times = Sat.ceilDiv(d, r.outputAmount());
        bumpFiring(r, times);

        long inputUnmet = 0;
        for (CraftInput<K> in : r.inputs()) {
            long amt = in.unitsFor(times); // closed form per flavour
            long unmet = obtain(in.key(), amt);
            inputUnmet = Sat.add(inputUnmet, unmet);
            if (in.returned() && in.uses() == CraftInput.INFINITE_USES) {
                // true catalyst/container: the seed is handed back, net consumption zero —
                // return what we actually got into the pool for reuse downstream. A finite-use
                // tool is degraded (consumed) by these firings, so nothing goes back.
                long returned = amt - unmet;
                if (returned > 0) {
                    bump(bpPool, in.key(), returned);
                }
            }
            if (search && missingTotal > entryMissing) {
                return inputUnmet; // a shortfall appeared; bail early, the caller will roll back
            }
        }

        long produced = Sat.mul(times, r.outputAmount());
        long surplus = produced - d;
        if (surplus > 0) {
            bump(bpPool, x, surplus);
        }
        for (CraftOutput<K> out : r.byproducts()) {
            bump(bpPool, out.key(), Sat.mul(out.amount(), times));
        }
        return inputUnmet;
    }

    /** Draw up to {@code d} of {@code x}: byproduct pool first, then inventory (counted as used stock). */
    private long drawPools(K x, long d) {
        long got = 0;
        long bp = Math.min(d, get(bpPool, x));
        if (bp > 0) {
            put(bpPool, x, get(bpPool, x) - bp);
            got += bp;
            d -= bp;
        }
        long st = Math.min(d, get(stockLeft, x));
        if (st > 0) {
            put(stockLeft, x, get(stockLeft, x) - st);
            put(usedStock, x, Sat.add(get(usedStock, x), st));
            got += st;
        }
        return got;
    }

    private List<CraftPattern<K>> byCapacityDesc(List<CraftPattern<K>> ps) {
        List<CraftPattern<K>> sorted = new ArrayList<>(ps);
        sorted.sort((a, b) -> Long.compare(producibleVia(b, capacity), producibleVia(a, capacity)));
        return sorted;
    }

    // ---- trail-logged mutation helpers -----------------------------------------------------------

    private long get(Map<K, Long> m, K k) {
        Long v = m.get(k);
        return v == null ? 0L : v;
    }

    private void put(Map<K, Long> m, K k, long newVal) {
        Long old = m.get(k);
        trail.push(() -> {
            if (old == null) {
                m.remove(k);
            } else {
                m.put(k, old);
            }
        });
        if (newVal == 0) {
            m.remove(k);
        } else {
            m.put(k, newVal);
        }
    }

    private void bump(Map<K, Long> m, K k, long delta) {
        if (delta == 0) {
            return;
        }
        put(m, k, Sat.add(get(m, k), delta));
    }

    private void addMissing(K k, long amt) {
        if (amt <= 0) {
            return;
        }
        put(missing, k, Sat.add(get(missing, k), amt));
        long old = missingTotal;
        trail.push(() -> missingTotal = old);
        missingTotal = Sat.add(missingTotal, amt);
    }

    private void bumpFiring(CraftPattern<K> r, long delta) {
        Long old = firings.get(r);
        trail.push(() -> {
            if (old == null) {
                firings.remove(r);
            } else {
                firings.put(r, old);
            }
        });
        firings.put(r, Sat.add(old == null ? 0L : old, delta));
    }

    private void rollback(int mark) {
        while (trail.size() > mark) {
            trail.pop().run();
        }
    }
}
