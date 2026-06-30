package com.qianchang.ae2lt_core.crafting.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot the planner runs against: patterns indexed by their output item, plus a
 * read-only inventory snapshot. The AE2 adapter builds this from {@code ICraftingService} +
 * the network storage snapshot; tests build it directly with {@code String} keys.
 *
 * @param <K> item key type
 */
public final class CraftGraph<K> {

    private final Map<K, List<CraftPattern<K>>> patternsByOutput;
    private final Map<K, Long> stock;

    private CraftGraph(Map<K, List<CraftPattern<K>>> patternsByOutput, Map<K, Long> stock) {
        this.patternsByOutput = patternsByOutput;
        this.stock = stock;
    }

    /** Patterns whose primary output is {@code key}, in caller-defined preference order. */
    public List<CraftPattern<K>> patternsFor(K key) {
        return patternsByOutput.getOrDefault(key, List.of());
    }

    /** Available amount of {@code key} in the snapshot (0 if none). */
    public long stock(K key) {
        Long v = stock.get(key);
        return v == null ? 0L : Math.max(0L, v);
    }

    public static <K> Builder<K> builder() {
        return new Builder<>();
    }

    public static final class Builder<K> {
        private final Map<K, List<CraftPattern<K>>> patterns = new HashMap<>();
        private final Map<K, Long> stock = new HashMap<>();

        /** Adds a pattern; patterns for the same output keep insertion order (= preference order). */
        public Builder<K> pattern(CraftPattern<K> pattern) {
            patterns.computeIfAbsent(pattern.output(), k -> new ArrayList<>()).add(pattern);
            return this;
        }

        public Builder<K> pattern(K output, long outAmount, List<CraftInput<K>> inputs) {
            return pattern(new CraftPattern<>(output, outAmount, inputs, null));
        }

        public Builder<K> pattern(K output, long outAmount, List<CraftInput<K>> inputs,
                                  List<CraftOutput<K>> byproducts) {
            return pattern(new CraftPattern<>(output, outAmount, inputs, byproducts, null));
        }

        public Builder<K> stock(K key, long amount) {
            stock.merge(key, amount, Long::sum);
            return this;
        }

        public CraftGraph<K> build() {
            Map<K, List<CraftPattern<K>>> frozen = new HashMap<>();
            patterns.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
            return new CraftGraph<>(frozen, Map.copyOf(stock));
        }
    }
}
