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
        String handlerSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorUndyingHandler.java"));
        String itemSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/UndyingSubmoduleItem.java"));
        String statusSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/hub/DeviceStatusModel.java"));
        String compactItemSource = itemSource.replaceAll("\\s+", "");
        String compactStatusSource = statusSource.replaceAll("\\s+", "");

        assertTrue(
                rulesSource.contains("public static final int UNDYING_COOLDOWN_TICKS = 0;"),
                "Undying should advertise no cooldown.");
        assertFalse(
                handlerSource.contains("UndyingSubmodule.getCooldown(active.armor()) > 0"),
                "Undying trigger should not be blocked by cooldown.");
        assertFalse(
                handlerSource.contains("UndyingSubmodule.setCooldown(active.armor()"),
                "Undying trigger should not write a cooldown after protecting the player.");
        assertTrue(
                compactItemSource.contains("newDeviceCapability.LastStandTuning(ArmorOverloadRules.UNDYING_TRIGGER_COST_FE,ArmorOverloadRules.UNDYING_COOLDOWN_TICKS,ArmorOverloadRules.UNDYING_COMBO_WINDOW_TICKS)"),
                "Undying capability should still expose the combo window while its cooldown value is zero.");
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
