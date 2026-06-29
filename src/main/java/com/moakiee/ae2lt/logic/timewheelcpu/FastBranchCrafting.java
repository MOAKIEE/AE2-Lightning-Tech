package com.moakiee.ae2lt.logic.timewheelcpu;

import java.util.function.LongPredicate;

public final class FastBranchCrafting {
    private FastBranchCrafting() {
    }

    public static long findMaxCraftable(long target, LongPredicate probe) {
        return findMaxCraftable(target, probe, null);
    }

    public static long findMaxCraftable(long target, LongPredicate probe, FastPlanningStats stats) {
        try {
            return findMaxCraftableChecked(target, probe::test, stats);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    public static long findMaxCraftableChecked(long target, CheckedLongPredicate probe)
            throws InterruptedException {
        return findMaxCraftableChecked(target, probe, null);
    }

    public static long findMaxCraftableChecked(long target, CheckedLongPredicate probe, FastPlanningStats stats)
            throws InterruptedException {
        if (target <= 0) {
            return 0;
        }

        if (probe.test(target)) {
            return target;
        }

        long low = 0;
        long high = target;
        while (low + 1 < high) {
            long midpoint = low + ((high - low) / 2);
            if (stats != null) {
                stats.recordBinarySearch();
            }
            if (probe.test(midpoint)) {
                low = midpoint;
            } else {
                high = midpoint;
            }
        }
        return low;
    }

    @FunctionalInterface
    public interface CheckedLongPredicate {
        boolean test(long amount) throws InterruptedException;
    }
}
