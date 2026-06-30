package com.qianchang.ae2lt_core.crafting.core;

import java.util.Map;

/**
 * Result of {@link CraftPlanner#plan}.
 *
 * @param supported     {@code false} means the fast path declined (e.g. recursion/cycle detected);
 *                      caller must fall back to AE2's simulator. When {@code false} all other fields
 *                      are empty/zero.
 * @param feasible      {@code true} if the requested amount can be fully crafted from current stock.
 *                      When {@code false}, {@link #missing} lists what is short (a partial plan is
 *                      still provided for the craftable part).
 * @param firings       pattern -> number of times to fire it (the compact plan). Keyed by pattern
 *                      object identity.
 * @param usedStock     item -> amount drawn directly from the inventory snapshot.
 * @param missing       item -> amount that could not be obtained (raw leaves under DEEP mode).
 * @param grossDemand   item -> total amount requested before drawing from stock (one entry per
 *                      visited item). Exposed so the AE2 adapter can reproduce AE2's byte accounting
 *                      ({@code addStackBytes} is charged on the pre-extraction request amount).
 * @param itemsProcessed number of distinct items visited in the demand pass. Exposed so tests can
 *                       assert the planner is O(reachable items), never exponential.
 * @param budgetExhausted {@code true} if the bounded search hit a node's per-node visit cap and had
 *                       to commit a best-effort result instead of continuing to search. This is the
 *                       only situation in which the plan may be worse than AE2's exhaustive simulator
 *                       (the chosen exhaustion policy returns best-effort rather than declining). On
 *                       normal graphs it stays {@code false}: contention never approaches the cap.
 * @param <K> item key type
 */
public record CraftPlan<K>(
        boolean supported,
        boolean feasible,
        Map<CraftPattern<K>, Long> firings,
        Map<K, Long> usedStock,
        Map<K, Long> missing,
        Map<K, Long> grossDemand,
        int itemsProcessed,
        boolean budgetExhausted) {

    public static <K> CraftPlan<K> unsupported() {
        return new CraftPlan<>(false, false, Map.of(), Map.of(), Map.of(), Map.of(), 0, false);
    }
}
