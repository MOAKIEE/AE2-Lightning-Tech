package com.moakiee.ae2lt.overload.armor.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ReachDistanceOptionTest {

    @Test
    void rangeChoicesProvideOneTwoAndFourTimesReachTiers() {
        assertEquals("1x", ReachDistanceOption.ONE.label());
        assertEquals("2x", ReachDistanceOption.TWO.label());
        assertEquals("4x", ReachDistanceOption.FOUR.label());

        assertEquals(8.0D, ReachDistanceOption.ONE.blockBonus(), 0.0001D);
        assertEquals(5.0D, ReachDistanceOption.ONE.entityBonus(), 0.0001D);
        assertEquals(24.0D, ReachDistanceOption.TWO.blockBonus(), 0.0001D);
        assertEquals(10.0D, ReachDistanceOption.TWO.entityBonus(), 0.0001D);
        assertEquals(48.0D, ReachDistanceOption.FOUR.blockBonus(), 0.0001D);
        assertEquals(16.0D, ReachDistanceOption.FOUR.entityBonus(), 0.0001D);
    }

    @Test
    void storedIdsAndLabelsResolveToReachOptions() {
        assertEquals(ReachDistanceOption.TWO, ReachDistanceOption.fromId("TWO"));
        assertEquals(ReachDistanceOption.TWO, ReachDistanceOption.fromId("2x"));
        assertEquals(ReachDistanceOption.FOUR, ReachDistanceOption.fromId("4x"));
    }

    @Test
    void unknownStoredValueFallsBackToOneTimesReach() {
        assertEquals(ReachDistanceOption.ONE, ReachDistanceOption.fromId("bad"));
    }
}
