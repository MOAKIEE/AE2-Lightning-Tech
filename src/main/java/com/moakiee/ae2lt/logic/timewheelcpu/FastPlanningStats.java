package com.moakiee.ae2lt.logic.timewheelcpu;

import java.util.Locale;

public final class FastPlanningStats {
    private long branches;
    private long fastBranches;
    private long legacyBranches;
    private long failures;
    private long binarySearches;
    private long fuzzyCandidates;
    private long fallbacks;
    private long startNanos;
    private long elapsedNanos;

    public void reset(long nowNanos) {
        this.branches = 0;
        this.fastBranches = 0;
        this.legacyBranches = 0;
        this.failures = 0;
        this.binarySearches = 0;
        this.fuzzyCandidates = 0;
        this.fallbacks = 0;
        this.startNanos = nowNanos;
        this.elapsedNanos = 0;
    }

    public void finish(long nowNanos) {
        this.elapsedNanos = Math.max(0, nowNanos - this.startNanos);
    }

    public void recordBranches(long count) {
        this.branches += Math.max(0, count);
    }

    public void recordFastBranch() {
        this.fastBranches++;
    }

    public void recordLegacyBranch() {
        this.legacyBranches++;
    }

    public void recordFailure() {
        this.failures++;
    }

    public void recordBinarySearch() {
        this.binarySearches++;
    }

    public void recordFuzzyCandidate() {
        this.fuzzyCandidates++;
    }

    public void recordFallback() {
        this.fallbacks++;
    }

    public long binarySearches() {
        return this.binarySearches;
    }

    public String summary(long amount, boolean fallback) {
        return String.format(
                Locale.ROOT,
                "amount=%d branches=%d fastBranches=%d legacyBranches=%d failures=%d "
                        + "binarySearches=%d fuzzyCandidates=%d fallbacks=%d timeMs=%.3f fallback=%s",
                amount,
                this.branches,
                this.fastBranches,
                this.legacyBranches,
                this.failures,
                this.binarySearches,
                this.fuzzyCandidates,
                this.fallbacks,
                this.elapsedNanos / 1_000_000.0D,
                fallback);
    }
}
