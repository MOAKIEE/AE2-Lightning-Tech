package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorUndyingNoCooldownTest {

    @Test
    void undyingUsesComboPenaltyWithoutCooldownGate() throws Exception {
        String rulesSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/ArmorOverloadRules.java"));
        String capabilitySource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/device/capability/DeviceCapability.java"));
        String handlerSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorUndyingHandler.java"));
        String moduleSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/UndyingSubmodule.java"));
        String itemSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/UndyingSubmoduleItem.java"));
        String statusSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/hub/DeviceStatusModel.java"));
        String compactItemSource = itemSource.replaceAll("\\s+", "");
        String compactStatusSource = statusSource.replaceAll("\\s+", "");
        String compactCapabilitySource = capabilitySource.replaceAll("\\s+", "");

        assertFalse(
                rulesSource.contains("UNDYING_COOLDOWN_TICKS"),
                "Undying should not keep a redundant cooldown constant.");
        assertTrue(
                capabilitySource.contains("record LastStandTuning(long feCost, int comboWindowTicks)"),
                "Last stand tuning should only describe cost and combo window.");
        assertFalse(
                compactCapabilitySource.contains("recordLastStandTuning(longfeCost,intcooldownTicks,intcomboWindowTicks)"),
                "Last stand tuning should not carry a removed cooldown value.");
        assertFalse(
                moduleSource.contains("UndyingCooldown"),
                "Undying module should not keep old cooldown NBT keys.");
        assertFalse(
                moduleSource.contains("getCooldown"),
                "Undying module should not expose a removed cooldown getter.");
        assertFalse(
                moduleSource.contains("setCooldown"),
                "Undying module should not expose a removed cooldown setter.");
        assertFalse(
                moduleSource.contains("tickCooldown"),
                "Undying module should not tick removed cooldown state.");
        assertFalse(
                handlerSource.contains("UndyingSubmodule.getCooldown(active.armor()) > 0"),
                "Undying trigger should not be blocked by cooldown.");
        assertFalse(
                handlerSource.contains("UndyingSubmodule.setCooldown(active.armor()"),
                "Undying trigger should not write a cooldown after protecting the player.");
        assertTrue(
                compactItemSource.contains("newDeviceCapability.LastStandTuning(ArmorOverloadRules.UNDYING_TRIGGER_COST_FE,ArmorOverloadRules.UNDYING_COMBO_WINDOW_TICKS)"),
                "Undying capability should expose trigger cost and combo window.");
        assertTrue(
                handlerSource.contains("int comboIndex = UndyingSubmodule.nextComboIndex(active.armor(), now);"),
                "Undying should still count rapid repeated triggers.");
        assertTrue(
                handlerSource.contains("scaledCost(active.tuning().feCost(), comboIndex)"),
                "Undying FE cost should still scale with combo index.");
        assertTrue(
                handlerSource.contains("scaledCost(AE2LTCommonConfig.overloadArmorUndyingEhvCost(), comboIndex)"),
                "Undying EHV cost should still scale with combo index.");
        assertFalse(
                compactStatusSource.contains("if(UndyingSubmodule.INSTANCE.id().equals(submoduleId)){returnUndyingSubmodule.getCooldown(armor);}"),
                "Device status should not show stale undying cooldown data.");
    }
}
