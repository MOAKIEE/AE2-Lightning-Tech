package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorReflectDamageBasisTest {

    @Test
    void reflectRunsDuringPreFromDamageBeforeArmorShieldMitigation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorDamageHandler.java"));

        int incomingCapture = source.indexOf("float incoming = event.getNewDamage();");
        int reflectCall = source.indexOf("reflectIncomingDamage(player, event.getSource(), incoming);");

        assertTrue(
                incomingCapture >= 0,
                "Reflect should use the incoming damage before staged mitigation changes it.");
        assertTrue(
                reflectCall > incomingCapture,
                "Reflect must run from Pre using the incoming damage so phase-shielded hits can still reflect.");
        assertFalse(
                source.contains("LivingDamageEvent.Post"),
                "Post is not fired when Pre reduces damage to zero, so reflect cannot depend on Post.");
    }

    @Test
    void reflectedDamageDoesNotTriggerRecursiveReflect() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorDamageHandler.java"));

        assertTrue(
                source.contains("REFLECTING_DAMAGE"),
                "Reflect should mark reflected damage so it does not recursively reflect itself.");
        assertTrue(
                source.contains("if (!isReflectingDamage())"),
                "The reflect path should be skipped while reflected damage is being applied.");
        assertTrue(
                source.contains("hurtWithReflectGuard(attacker, source, reflected)"),
                "Reflected damage should be applied under the recursion guard.");
    }
}
