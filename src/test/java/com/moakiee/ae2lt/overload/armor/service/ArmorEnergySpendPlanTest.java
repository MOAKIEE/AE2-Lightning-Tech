package com.moakiee.ae2lt.overload.armor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class ArmorEnergySpendPlanTest {

    @Test
    void spendsPreferredArmorBeforeFallbackArmor() {
        var plan = ArmorEnergySpendPlan.create(
                100L,
                List.of(
                        new ArmorEnergySpendPlan.Source(0, 40L),
                        new ArmorEnergySpendPlan.Source(1, 70L),
                        new ArmorEnergySpendPlan.Source(2, 100L)));

        assertTrue(plan.canPay());
        assertEquals(
                List.of(
                        new ArmorEnergySpendPlan.Debit(0, 40L),
                        new ArmorEnergySpendPlan.Debit(1, 60L)),
                plan.debits());
    }

    @Test
    void refusesPartialSpendWhenCombinedArmorEnergyIsInsufficient() {
        var plan = ArmorEnergySpendPlan.create(
                100L,
                List.of(
                        new ArmorEnergySpendPlan.Source(0, 40L),
                        new ArmorEnergySpendPlan.Source(1, 30L)));

        assertFalse(plan.canPay());
        assertEquals(List.of(), plan.debits());
    }
}
