package com.moakiee.ae2lt.client.railgun;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side visual utilities shared by railgun renderers. Centralizes the
 * "compute the gun barrel position from a player's pose" logic so both the
 * persistent left-beam and the one-shot charged plasma trail emanate from the
 * weapon model rather than the screen-center / eye position.
 *
 * <p>All methods accept a {@code partialTick} argument so the result is
 * interpolated to the current render frame; without this the barrel snaps in
 * 50 ms (= one-tick) increments while the camera renders at 60+ fps, which
 * shows up as visible jitter on the beam during movement.
 */
public final class RailgunVisuals {

    /**
     * Horizontal offset to the right of the player's view for the muzzle.
     * Positive = right hand side; flipped for left-handed players.
     */
    private static final double SIDE_OFFSET = 0.22D;
    /** How far in front of the eye along the look vector the muzzle sits. */
    private static final double FORWARD_OFFSET = 0.55D;
    /** How far below the eye (in the view's up axis) the muzzle sits. */
    private static final double VERTICAL_OFFSET = -0.18D;

    private RailgunVisuals() {}

    /**
     * Compute the world-space position of the gun muzzle tip for the given
     * player, interpolated to the current render-frame partial tick.
     *
     * <p>All three offsets (forward / right / up) are expressed in the player's
     * view frame, so the muzzle behaves as a point rigidly attached in front of
     * the camera: it tracks pitch and yaw together and the beam always appears
     * to emerge from the same spot on the gun, no matter where the player looks.
     */
    public static Vec3 computeBarrelOrigin(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick).normalize();
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = look.cross(worldUp);
        if (right.lengthSqr() < 1.0E-6D) {
            // Looking straight up/down — fall back to yaw-derived right axis.
            float yawRad = player.getViewYRot(partialTick) * (float) (Math.PI / 180.0);
            right = new Vec3(-Math.cos(yawRad), 0.0D, -Math.sin(yawRad));
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(look).normalize();
        boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        double sideMul = leftHanded ? -1.0D : 1.0D;
        return eye
                .add(look.scale(FORWARD_OFFSET))
                .add(right.scale(SIDE_OFFSET * sideMul))
                .add(up.scale(VERTICAL_OFFSET));
    }

    /**
     * Resolve the barrel origin for the player identified by {@code shooterId},
     * falling back to {@code fallback} (typically the server-supplied eye
     * position) if the player isn't loaded clientside.
     */
    public static Vec3 resolveShooterBarrel(@Nullable UUID shooterId, Vec3 fallback, float partialTick) {
        if (shooterId == null) return fallback;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return fallback;
        Player p = mc.level.getPlayerByUUID(shooterId);
        if (p == null) return fallback;
        return computeBarrelOrigin(p, partialTick);
    }

    /** Convenience: current frame's partial tick from Minecraft's delta tracker. */
    public static float currentPartialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }
}
