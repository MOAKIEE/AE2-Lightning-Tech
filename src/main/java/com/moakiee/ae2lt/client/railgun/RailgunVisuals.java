package com.moakiee.ae2lt.client.railgun;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;

/**
 * Client-side visual utilities for railgun beams. Each frame, callers compose:
 *   origin    = {@link #computeBarrelOrigin}
 *   direction = {@link #computeBarrelDirection}
 *   endpoint  = {@link #computeBarrelEndpoint}
 *
 * <p>Third-person beam direction uses {@code yHeadRot} (not {@code yRot}). The
 * model head is driven by {@code yHeadRot}, which only syncs to {@code yRot}
 * once per tick in {@code Player#aiStep}; using {@code yRot} here would race
 * ahead of the rendered barrel by up to one tick (~50 ms) during fast camera
 * motion. Server-supplied {@code (to - from)} is also unsuitable for direction
 * since it's frozen at packet-send time.
 */
public final class RailgunVisuals {

    // First-person muzzle offsets (view-space). Origin sits at the gun's
    // centerline between the four rails, slightly forward of the receiver —
    // makes the beam appear to emerge from inside the rail cage.
    private static final double FP_SIDE_OFFSET = 0.56D;
    private static final double FP_FORWARD_OFFSET = 1.40D;
    private static final double FP_VERTICAL_OFFSET = -0.40D;

    // Third-person muzzle geometry. Shoulder anchored on body yaw (lags head),
    // muzzle extended along view direction (tracks head instantly).
    private static final double TP_SHOULDER_HEIGHT_FACTOR = 0.78D;
    private static final double TP_SHOULDER_SIDE = 0.3125D;
    private static final double TP_BARREL_LENGTH = 1.18D;

    private RailgunVisuals() {}

    /** World-space muzzle position for the given player at the current frame. */
    public static Vec3 computeBarrelOrigin(Player player, float partialTick) {
        if (isLocalFirstPerson(player)) {
            return computeFirstPersonBarrelOrigin(player);
        }
        return computeThirdPersonBarrelOrigin(player, partialTick);
    }

    /**
     * Normalized barrel-aligned direction. Local first-person uses the camera
     * look vector. Third-person / remote use {@code yHeadRot + xRot} to match
     * the rendered model head (see class javadoc).
     */
    public static Vec3 computeBarrelDirection(Player player, float partialTick) {
        if (isLocalFirstPerson(player)) {
            Vec3 cam = fromJoml(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
            if (cam.lengthSqr() < 1.0E-9D) {
                return new Vec3(1.0D, 0.0D, 0.0D);
            }
            return cam.normalize();
        }
        float yaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        return Vec3.directionFromRotation(pitch, yaw);
    }

    /**
     * Compose the beam endpoint: muzzle + barrelDir * length, where length is
     * the server-reported travel distance. For remote players, an additional
     * client raycast clamps the endpoint to nearby geometry so the beam never
     * appears to dangle in midair during a fast head whip.
     */
    public static Vec3 computeBarrelEndpoint(Player player, Vec3 origin,
                                             Vec3 shotFrom, Vec3 shotTo, float partialTick) {
        Vec3 dir = computeBarrelDirection(player, partialTick);
        double length = shotTo.subtract(shotFrom).length();
        if (length < 1.0E-3D) {
            return origin;
        }
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player && mc.level != null) {
            Vec3 maxEnd = origin.add(dir.scale(RailgunDefaults.BEAM_RANGE));
            HitResult hit = mc.level.clip(new ClipContext(
                    origin, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                double clipLen = hit.getLocation().subtract(origin).length();
                length = Math.min(length, clipLen);
            }
        }
        return origin.add(dir.scale(length));
    }

    /** Current frame's partial tick from Minecraft's delta tracker. */
    public static float currentPartialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    private static boolean isLocalFirstPerson(Player player) {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    private static Vec3 computeFirstPersonBarrelOrigin(Player player) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        Vec3 look = fromJoml(camera.getLookVector()).normalize();
        Vec3 right = fromJoml(camera.getLeftVector()).scale(-1.0D).normalize();
        Vec3 up = right.cross(look).normalize();
        double sideMul = holdingArm(player) == HumanoidArm.LEFT ? -1.0D : 1.0D;
        return eye
                .add(look.scale(FP_FORWARD_OFFSET))
                .add(right.scale(FP_SIDE_OFFSET * sideMul))
                .add(up.scale(FP_VERTICAL_OFFSET));
    }

    /**
     * Third-person muzzle: shoulder anchored on {@code yBodyRot} (lags head,
     * keeps the shoulder stable mid-whip), then extended along the rendered
     * barrel direction so the muzzle swings with the head — matches the model.
     */
    private static Vec3 computeThirdPersonBarrelOrigin(Player player, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float bodyYawRad = bodyYaw * (float) (Math.PI / 180.0D);
        Vec3 bodyRight = new Vec3(-Math.cos(bodyYawRad), 0.0D, -Math.sin(bodyYawRad));
        double sideMul = holdingArm(player) == HumanoidArm.LEFT ? -1.0D : 1.0D;
        Vec3 lookDir = computeBarrelDirection(player, partialTick);
        return player.getPosition(partialTick)
                .add(0.0D, player.getBbHeight() * TP_SHOULDER_HEIGHT_FACTOR, 0.0D)
                .add(bodyRight.scale(TP_SHOULDER_SIDE * sideMul))
                .add(lookDir.scale(TP_BARREL_LENGTH));
    }

    private static Vec3 fromJoml(Vector3f v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static HumanoidArm holdingArm(Player player) {
        if (player.getMainHandItem().getItem() instanceof ElectromagneticRailgunItem) {
            return player.getMainArm();
        }
        if (player.getOffhandItem().getItem() instanceof ElectromagneticRailgunItem) {
            return player.getMainArm().getOpposite();
        }
        return player.getUsedItemHand() == InteractionHand.OFF_HAND
                ? player.getMainArm().getOpposite()
                : player.getMainArm();
    }
}
