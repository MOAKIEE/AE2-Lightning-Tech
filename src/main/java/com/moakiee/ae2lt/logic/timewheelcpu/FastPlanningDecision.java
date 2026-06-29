package com.moakiee.ae2lt.logic.timewheelcpu;

public final class FastPlanningDecision {
    private FastPlanningDecision() {
    }

    public static boolean useLegacyNode(int branchCount) {
        return branchCount <= 1;
    }

    public static boolean useBulkBranch(int branchCount, boolean limitQty) {
        return branchCount > 1 && !limitQty;
    }

    public static boolean useFastAttempt(boolean fastEnabled, boolean simulation) {
        return fastEnabled && !simulation;
    }
}
