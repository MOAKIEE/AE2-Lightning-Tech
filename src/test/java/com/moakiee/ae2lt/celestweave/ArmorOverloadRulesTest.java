package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ArmorOverloadRulesTest {
    @Test
    void armorModuleFeCostsAreScaledUp() {
        assertEquals(2_000L, ArmorOverloadRules.NIGHT_VISION_PASSIVE_DRAIN_FE);
        assertEquals(2_000L, ArmorOverloadRules.WATER_BREATHING_PASSIVE_DRAIN_FE);
        assertEquals(10_000L, ArmorOverloadRules.RESISTANCE_PASSIVE_DRAIN_FE);
        assertEquals(6_000L, ArmorOverloadRules.REFLECT_PASSIVE_DRAIN_FE);
        assertEquals(300_000L, ArmorOverloadRules.REFLECT_ACTIVE_COST_FE_PER_DAMAGE);
        assertEquals(1_000L, ArmorOverloadRules.DASH_PASSIVE_DRAIN_FE);
        assertEquals(5_000_000L, ArmorOverloadRules.DASH_ACTIVE_COST_FE);
        assertEquals(50_000L, ArmorOverloadRules.FLIGHT_HOVER_DRAIN_FE);
        assertEquals(200_000L, ArmorOverloadRules.FLIGHT_MOVING_DRAIN_FE);
        assertEquals(6_000L, ArmorOverloadRules.PURIFICATION_PASSIVE_DRAIN_FE);
        assertEquals(1_200L, ArmorOverloadRules.SATURATION_PASSIVE_DRAIN_FE);
        assertEquals(1_800L, ArmorOverloadRules.DIG_AFFINITY_PASSIVE_DRAIN_FE);
        assertEquals(2_500L, ArmorOverloadRules.REACH_EXTENSION_PASSIVE_DRAIN_FE);
        assertEquals(400_000L, ArmorOverloadRules.PHASE_FLIGHT_PASSIVE_DRAIN_FE);
    }

    @Test
    void undyingFeCostsOnlyDouble() {
        assertEquals(4_000L, ArmorOverloadRules.UNDYING_PASSIVE_DRAIN_FE);
        assertEquals(2_000_000_000L, ArmorOverloadRules.UNDYING_TRIGGER_COST_FE);
    }
}
