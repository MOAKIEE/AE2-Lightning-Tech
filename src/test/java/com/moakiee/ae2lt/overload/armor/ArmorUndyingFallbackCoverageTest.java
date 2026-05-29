package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorUndyingFallbackCoverageTest {

    @Test
    void undyingCoversIncomingHardFatalDamageBeforeDamagePre() throws Exception {
        String handlerSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorUndyingHandler.java"));
        String compactHandler = handlerSource.replaceAll("\\s+", "");

        assertTrue(
                handlerSource.contains("LivingIncomingDamageEvent"),
                "Undying should hook the earliest NeoForge living damage event as a fallback.");
        assertTrue(
                handlerSource.contains("onIncomingFatalDamage(LivingIncomingDamageEvent event)"),
                "Undying should have a dedicated incoming-damage fallback handler.");
        assertTrue(
                compactHandler.contains("!isIncomingUndyingCandidate(event.getSource())"),
                "The incoming fallback should be limited to hard or forced damage sources.");
        assertTrue(
                compactHandler.contains("if(tryProtectWithinWindow(player,now)){event.setAmount(0.0F);event.setCanceled(true);}"),
                "Incoming fatal damage should reuse an already paid protection window.");
        assertTrue(
                compactHandler.contains("elseif(tryTrigger(player,now)){event.setAmount(0.0F);event.setCanceled(true);}"),
                "Incoming fatal damage should trigger undying and cancel the damage sequence.");
        assertTrue(
                handlerSource.contains("DamageTypeTags.BYPASSES_INVULNERABILITY")
                        && handlerSource.contains("DamageTypes.GENERIC_KILL")
                        && handlerSource.contains("DamageTypes.FELL_OUT_OF_WORLD"),
                "The incoming fallback should include forced-kill style damage sources.");
    }

    @Test
    void undyingChecksDeadStateBeforeAndAfterPlayerTick() throws Exception {
        String handlerSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorUndyingHandler.java"));
        String compactHandler = handlerSource.replaceAll("\\s+", "");

        assertTrue(
                handlerSource.contains("onPlayerTickPre(PlayerTickEvent.Pre event)"),
                "Undying should check forced-death state at the start of player tick.");
        assertTrue(
                handlerSource.contains("onPlayerTick(PlayerTickEvent.Post event)"),
                "Undying should keep the existing end-of-tick forced-death check.");
        assertTrue(
                compactHandler.contains("tryProtectDeadOrDying(event.getEntity());"),
                "Both tick handlers should delegate to the same forced-death check.");
    }
}
