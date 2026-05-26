package com.moakiee.ae2lt.item.railgun;

final class RailgunStructuralCoreRules {
    static final String ULTIMATE_OVERLOAD_CORE_ID = "ae2lt:ultimate_overload_core";
    static final int ULTIMATE_CORE_OVERLOAD_BUDGET = RailgunOverloadRules.DYNAMIC_CAP;

    private RailgunStructuralCoreRules() {
    }

    static int baseOverloadBudgetForItemId(String itemId) {
        return ULTIMATE_OVERLOAD_CORE_ID.equals(itemId) ? ULTIMATE_CORE_OVERLOAD_BUDGET : 0;
    }
}
