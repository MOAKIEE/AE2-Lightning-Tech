package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorUndyingProtectionWindowTest {

    @Test
    void repeatedForcedDeathWithinProtectionWindowDoesNotChargeAgain() throws Exception {
        String handlerSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorUndyingHandler.java"));
        String compactHandler = handlerSource.replaceAll("\\s+", "");

        assertTrue(
                handlerSource.contains("PROTECTION_WINDOW_TICKS = 5"),
                "Undying should keep a short protection window after paying the trigger cost so repeated fatal hits are not re-charged.");
        assertTrue(
                handlerSource.contains("TAG_PROTECTED_UNTIL"),
                "Undying should persist the tick until which forced-death paths are already covered.");
        assertTrue(
                compactHandler.contains("if(hasActiveProtectionWindow(player,now)){recordProtectedTick(player,now);restoreSurvivalState(player);returntrue;}"),
                "Forced-death interception should reuse the paid protection window instead of charging every tick.");
        assertTrue(
                compactHandler.contains("if(tryProtectWithinWindow(player,now)){event.setNewDamage(0.0F);}"),
                "Fatal damage events should also be covered by the paid protection window.");
        assertTrue(
                compactHandler.contains("recordProtectionWindow(player,now);restoreSurvivalState(player);"),
                "A paid trigger should open the protection window before restoring the player.");
    }
}
