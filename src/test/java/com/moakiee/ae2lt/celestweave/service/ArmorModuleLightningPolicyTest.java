package com.moakiee.ae2lt.celestweave.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.capability.FlightKind;

final class ArmorModuleLightningPolicyTest {
    @Test
    void normalModulesNoLongerConsumePassiveLightning() {
        var cost = ArmorModuleLightningPolicy.passiveCost(
                List.of(new DeviceCapability.PassiveDrain(200L)),
                false,
                1L,
                2L,
                8L);

        assertEquals(0L, cost.highVoltage());
        assertEquals(0L, cost.extremeHighVoltage());
    }

    @Test
    void reachExtensionKeepsPassiveLightningCost() {
        var cost = ArmorModuleLightningPolicy.passiveCost(
                List.of(new DeviceCapability.InteractionRange()),
                false,
                1L,
                2L,
                8L);

        assertEquals(1L, cost.highVoltage());
        assertEquals(0L, cost.extremeHighVoltage());
    }

    @Test
    void creativeFlightKeepsMovingPassiveLightningCost() {
        var cost = ArmorModuleLightningPolicy.passiveCost(
                List.of(new DeviceCapability.FlightMode(FlightKind.CREATIVE)),
                true,
                1L,
                2L,
                8L);

        assertEquals(4L, cost.highVoltage());
        assertEquals(0L, cost.extremeHighVoltage());
    }

    @Test
    void phaseFlightKeepsPassiveLightningCost() {
        var cost = ArmorModuleLightningPolicy.passiveCost(
                List.of(new DeviceCapability.FlightMode(FlightKind.PHASE)),
                true,
                1L,
                2L,
                8L);

        assertEquals(8L, cost.highVoltage());
        assertEquals(0L, cost.extremeHighVoltage());
    }

    @Test
    void removedTriggeredArmorModuleCostsAreZero() {
        for (var trigger : List.of(
                ArmorModuleLightningPolicy.Trigger.DASH,
                ArmorModuleLightningPolicy.Trigger.MATRIX_SHIELD,
                ArmorModuleLightningPolicy.Trigger.PHASE_SHIELD,
                ArmorModuleLightningPolicy.Trigger.REFLECT,
                ArmorModuleLightningPolicy.Trigger.UNDYING,
                ArmorModuleLightningPolicy.Trigger.PURIFICATION,
                ArmorModuleLightningPolicy.Trigger.SATURATION,
                ArmorModuleLightningPolicy.Trigger.DIG_AFFINITY)) {
            var cost = ArmorModuleLightningPolicy.triggeredCost(trigger);

            assertEquals(0L, cost.highVoltage(), trigger.name());
            assertEquals(0L, cost.extremeHighVoltage(), trigger.name());
        }
    }

}
