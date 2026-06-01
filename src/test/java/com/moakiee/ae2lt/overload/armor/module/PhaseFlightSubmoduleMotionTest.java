package com.moakiee.ae2lt.overload.armor.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class PhaseFlightSubmoduleMotionTest {

    @Test
    void phaseMotionUsesVanillaFlyingSpeedForCtrlAcceleration() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/PhaseFlightSubmodule.java"));

        assertTrue(
                source.contains("abilities.setFlyingSpeed"),
                "Phase flight should use vanilla flying speed so Ctrl acceleration stays handled by vanilla movement.");
    }

    @Test
    void phaseMotionDoesNotReadServerPlayerMovementAxes() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/PhaseFlightSubmodule.java"));

        assertTrue(
                !source.contains("player.zza") && !source.contains("player.xxa") && !source.contains("player.yya")
                        && !source.contains("setDeltaMovement(motion"),
                "Phase flight should not drive pseudo-spectator motion from server-only Player input axes.");
    }

    @Test
    void transientPhaseStateClearsOnGroundSoClientFlightIsNotCancelled() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/PhaseFlightSubmodule.java"));

        assertTrue(
                source.contains("player.setOnGround(false)"),
                "Phase flight must keep onGround false; LocalPlayer.aiStep cancels flying when onGround remains true.");
    }

    @Test
    void clientPhaseHandlerKeepsLocalFlyingAbilityStable() throws Exception {
        String handler = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ClientPhaseFlightHandler.java"));
        String submodule = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/PhaseFlightSubmodule.java"));

        assertTrue(
                handler.contains("applyClientPhaseFlightState"),
                "Client phase flight should force the local mayfly/flying ability before vanilla movement runs.");
        assertTrue(
                submodule.contains("applyClientPhaseFlightState")
                        && submodule.contains("abilities.flying = true")
                        && submodule.contains("abilities.mayfly = true"),
                "Client phase flight needs a local ability helper so W movement uses vanilla flying travel every tick.");
    }

    @Test
    void clientPhaseAbilityHelperDoesNotResetConfiguredFlyingSpeed() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/PhaseFlightSubmodule.java"));
        int helperStart = source.indexOf("applyClientPhaseFlightState");
        int helperEnd = source.indexOf("public static void clearTransientPhaseState", helperStart);
        String helper = source.substring(helperStart, helperEnd);

        assertTrue(
                !helper.contains("setFlyingSpeed") && !helper.contains("VANILLA_FLYING_SPEED"),
                "Client phase flight should preserve the server-synced 1x/2x/4x flying speed instead of resetting it locally.");
    }
}
