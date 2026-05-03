package com.moakiee.ae2lt.client.railgun;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.network.railgun.RailgunBeamUpdatePacket;

/**
 * Renders persistent left-click beams.
 *
 * <p>Visual model: each active shooter has a {@link BeamState} carrying the
 * server-supplied eye position, the impact endpoint, and bookkeeping ticks.
 * Each frame, the renderer:
 * <ul>
 *   <li>Resolves the player's current gun-barrel position (so the beam visibly
 *       emanates from the weapon, not the screen-center).</li>
 *   <li>Subdivides the beam into several short prism segments, each with
 *       independent vertex-color alpha that animates over time. This produces a
 *       slow "energy flow" that travels along the beam.</li>
 *   <li>Stacks three rectangular-prism layers per segment (outer halo + mid +
 *       core), each rotating around the beam axis at a different rate so the
 *       beam reads as a glowing cyan-white plasma stream with real volume from
 *       any viewing angle.</li>
 *   <li>Adds a camera-facing flash quad at the impact tip that tracks the
 *       breath pulse for a "burning hot endpoint" feel.</li>
 *   <li>Periodically (every {@value #ARC_INTERVAL_TICKS} ticks per beam)
 *       branches a short electric arc off a random point along the beam via
 *       {@link RailgunArcRenderer} for crackle.</li>
 * </ul>
 *
 * <p>Stale beam states (no packet for {@value #STALE_TICKS} ticks) self-expire
 * to recover from missed stop signals.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunBeamRenderClient {

    private static final Map<UUID, BeamState> ACTIVE = new ConcurrentHashMap<>();
    private static final long STALE_TICKS = 6L;
    private static final float OUTER_RADIUS = 0.20F;
    private static final float MID_RADIUS = 0.11F;
    private static final float CORE_RADIUS = 0.038F;
    /** Number of sub-segments along the beam length for the energy-flow effect. */
    private static final int BEAM_SEGMENTS = 12;
    /** How fast the breath pulse oscillates (rad / tick). Lower = slower breath. */
    private static final double PULSE_RATE = 0.30D;
    /** How fast the energy-flow stripes scroll along the beam (rad / tick). */
    private static final double FLOW_RATE = 0.22D;
    /** Self-rotation speeds (rad / tick) of each prism layer around the beam axis. */
    private static final double SPIN_OUTER = 0.18D;
    private static final double SPIN_MID = -0.32D;
    private static final double SPIN_CORE = 0.55D;
    /** Tick spacing between auto-spawned crackle arcs per beam. */
    private static final long ARC_INTERVAL_TICKS = 3L;
    private static volatile boolean localFiring = false;

    /** Per-shooter beam state (mutable; updated in place by packet handler). */
    public static final class BeamState {
        public final UUID shooterId;
        public Vec3 from;
        public Vec3 to;
        public long lastUpdateTick;
        public long lastArcTick;

        BeamState(UUID shooterId, Vec3 f, Vec3 t, long tick) {
            this.shooterId = shooterId;
            this.from = f;
            this.to = t;
            this.lastUpdateTick = tick;
            this.lastArcTick = tick;
        }
    }

    private RailgunBeamRenderClient() {}

    public static void setLocalFiring(boolean firing) {
        localFiring = firing;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!firing) {
            ACTIVE.remove(mc.player.getUUID());
        } else {
            long tick = mc.level == null ? 0L : mc.level.getGameTime();
            ACTIVE.put(mc.player.getUUID(), new BeamState(mc.player.getUUID(),
                    mc.player.getEyePosition(),
                    mc.player.getEyePosition().add(mc.player.getLookAngle().scale(RailgunDefaults.BEAM_RANGE)),
                    tick));
        }
    }

    public static void applyUpdate(RailgunBeamUpdatePacket p) {
        Minecraft mc = Minecraft.getInstance();
        long tick = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        if (!p.active()) {
            ACTIVE.remove(p.shooterId());
            if (mc.player != null && p.shooterId().equals(mc.player.getUUID())) {
                localFiring = false;
            }
            return;
        }
        if (mc.player != null && p.shooterId().equals(mc.player.getUUID()) && !localFiring) {
            return;
        }
        ACTIVE.compute(p.shooterId(), (k, prev) -> {
            if (prev == null) {
                return new BeamState(p.shooterId(), p.from(), p.to(), tick);
            }
            prev.from = p.from();
            prev.to = p.to();
            prev.lastUpdateTick = tick;
            return prev;
        });
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        // Interpolation factor for the current render frame; without this every
        // {@code getEyePosition()}/{@code getYRot()} call snaps in 50 ms steps and the
        // beam visibly stutters during movement.
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        refreshLocalBeam(mc, now, partialTick);
        ACTIVE.entrySet().removeIf(en -> now - en.getValue().lastUpdateTick > STALE_TICKS);
        if (ACTIVE.isEmpty()) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack stack = e.getPoseStack();
        stack.pushPose();
        stack.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        // Additive blending for the bright plasma feel.
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Slow-breath pulse that affects all layers' alpha. Range ~0.85..1.05.
        // Use partial-tick-interpolated time for smooth animation between ticks.
        double smoothTime = now + partialTick;
        float pulse = 0.95F + 0.10F * (float) Math.sin(smoothTime * PULSE_RATE);

        var bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var matrix = stack.last().pose();
        for (BeamState s : ACTIVE.values()) {
            // Resolve dynamic origin per-frame so the beam tracks the player's gun barrel
            // even when other players move between server packets.
            Vec3 origin = resolveOriginFor(s, mc, partialTick);
            addBeam(bb, matrix, origin, s.to, camPos, pulse, smoothTime);
            addEndpointGlow(bb, matrix, s.to, camPos, pulse);
        }
        var built = bb.build();
        if (built != null) {
            BufferUploader.drawWithShader(built);
        }

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        stack.popPose();

        // Crackle arcs (use the shared bolt renderer; runs in its own pass).
        spawnCrackleArcs(mc, now, partialTick);
    }

    /**
     * Resolve the visual origin of the beam -- preferred is the gun barrel
     * position computed from the shooter's current (interpolated) pose;
     * fallback to the server-provided eye position if the shooter isn't loaded.
     */
    private static Vec3 resolveOriginFor(BeamState s, Minecraft mc, float partialTick) {
        if (mc.level == null) return s.from;
        Player p = mc.level.getPlayerByUUID(s.shooterId);
        if (p == null) return s.from;
        return RailgunVisuals.computeBarrelOrigin(p, partialTick);
    }

    private static void refreshLocalBeam(Minecraft mc, long now, float partialTick) {
        if (!localFiring || mc.player == null || mc.level == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof ElectromagneticRailgunItem) || mc.screen != null) {
            setLocalFiring(false);
            return;
        }

        // The beam's visual origin is the gun barrel (see RailgunVisuals), so the
        // client-side raycast must also start there and follow the player's look
        // direction. Previously the raycast started at the eye position, which
        // left the barrel origin off-axis and made the beam appear to emanate
        // from a point floating in front of the player at steep pitches. The
        // server still authoritatively computes damage from the eye position.
        Vec3 from = RailgunVisuals.computeBarrelOrigin(mc.player, partialTick);
        Vec3 dir = mc.player.getViewVector(partialTick).normalize();
        double range = RailgunDefaults.BEAM_RANGE;
        Vec3 maxTo = from.add(dir.scale(range));
        HitResult blockHit = mc.level.clip(new ClipContext(
                from, maxTo, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        Vec3 endBlock = blockHit.getType() == HitResult.Type.MISS ? maxTo : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(mc.level, mc.player, from, endBlock,
                new AABB(from, endBlock).inflate(0.5D),
                e -> e instanceof LivingEntity le && le != mc.player && !le.isSpectator());
        Vec3 to = entityHit == null ? endBlock : entityHit.getLocation();
        ACTIVE.compute(mc.player.getUUID(), (k, prev) -> {
            if (prev == null) {
                return new BeamState(mc.player.getUUID(), from, to, now);
            }
            prev.from = from;
            prev.to = to;
            prev.lastUpdateTick = now;
            return prev;
        });
    }

    private static void addBeam(BufferBuilder bb, org.joml.Matrix4f matrix, Vec3 origin, Vec3 endpoint,
                                Vec3 cameraPos, float pulse, double smoothTime) {
        Vec3 axis = endpoint.subtract(origin);
        double len = axis.length();
        if (len < 1.0E-3D) return;

        Vec3 dir = axis.scale(1.0D / len);
        // Build a fixed world-space orthonormal basis in the plane perpendicular to
        // dir. Decoupling from the camera is what turns this from a flat billboard
        // into real volume -- the prism keeps its shape when viewed from any angle.
        Vec3 helper = Math.abs(dir.y) < 0.95D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 e1 = dir.cross(helper).normalize();
        Vec3 e2 = dir.cross(e1).normalize();

        // Each layer spins around dir at its own rate for a "twisting plasma" feel.
        double angOuter = smoothTime * SPIN_OUTER;
        double angMid = smoothTime * SPIN_MID;
        double angCore = smoothTime * SPIN_CORE;
        Vec3[] outerBasis = rotateBasis(e1, e2, angOuter);
        Vec3[] midBasis = rotateBasis(e1, e2, angMid);
        Vec3[] coreBasis = rotateBasis(e1, e2, angCore);

        // Sub-segment the beam so vertex-color interpolation can paint a traveling
        // energy wave along its length. Each segment also slightly tapers from the
        // muzzle (thicker) to the impact tip (thinner) for a sense of depth.
        for (int i = 0; i < BEAM_SEGMENTS; i++) {
            float t0 = i / (float) BEAM_SEGMENTS;
            float t1 = (i + 1) / (float) BEAM_SEGMENTS;
            Vec3 a = origin.add(axis.scale(t0));
            Vec3 b = origin.add(axis.scale(t1));
            // Subtle taper: 1.0 at the muzzle, 0.78 at the tip.
            float taper0 = 1.0F - 0.22F * t0;
            float taper1 = 1.0F - 0.22F * t1;
            // Energy flow: bright "stripes" travel from muzzle toward impact (smooth between ticks).
            float flow0 = 0.55F + 0.45F * (float) Math.sin(t0 * 9.0F - smoothTime * FLOW_RATE);
            float flow1 = 0.55F + 0.45F * (float) Math.sin(t1 * 9.0F - smoothTime * FLOW_RATE);
            float a0 = pulse * flow0;
            float a1 = pulse * flow1;

            // Outer wide blue halo -- soft and large; lower alpha than the old
            // billboard because a 4-sided prism exposes twice the area.
            addPrismSegment(bb, matrix, a, b, outerBasis,
                    OUTER_RADIUS * taper0, OUTER_RADIUS * taper1,
                    0.22F, 0.58F, 1.00F, 0.18F * a0, 0.18F * a1);
            // Mid bright cyan -- most of the beam's color comes from here.
            addPrismSegment(bb, matrix, a, b, midBasis,
                    MID_RADIUS * taper0, MID_RADIUS * taper1,
                    0.52F, 0.88F, 1.00F, 0.34F * a0, 0.34F * a1);
            // Hot near-white core -- narrow and bright, with extra pulse intensity.
            float coreA0 = Math.min(1.0F, 1.10F * a0);
            float coreA1 = Math.min(1.0F, 1.10F * a1);
            addPrismSegment(bb, matrix, a, b, coreBasis,
                    CORE_RADIUS * taper0, CORE_RADIUS * taper1,
                    0.95F, 1.00F, 1.00F, 0.70F * coreA0, 0.70F * coreA1);
        }
    }

    /** Rotate the (e1,e2) basis around dir by {@code angle} radians. */
    private static Vec3[] rotateBasis(Vec3 e1, Vec3 e2, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        Vec3 r1 = e1.scale(c).add(e2.scale(s));
        Vec3 r2 = e1.scale(-s).add(e2.scale(c));
        return new Vec3[] { r1, r2 };
    }

    /**
     * Draw a hot camera-facing flash quad at the beam's impact point. Sells the
     * "burning into the surface" feel; tracks the breath pulse.
     */
    private static void addEndpointGlow(BufferBuilder bb, org.joml.Matrix4f matrix, Vec3 center,
                                        Vec3 cameraPos, float pulse) {
        Vec3 toCam = cameraPos.subtract(center);
        if (toCam.lengthSqr() < 1.0E-6D) return;
        Vec3 fwd = toCam.normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = fwd.cross(up);
        if (right.lengthSqr() < 1.0E-9D) {
            right = fwd.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        float radius = 0.55F * pulse;
        right = right.normalize().scale(radius);
        Vec3 vUp = right.cross(fwd).normalize().scale(radius);
        Vec3 p1 = center.add(right).add(vUp);
        Vec3 p2 = center.subtract(right).add(vUp);
        Vec3 p3 = center.subtract(right).subtract(vUp);
        Vec3 p4 = center.add(right).subtract(vUp);
        // Bright blue-white burst, faded edges via per-vertex alpha.
        bb.addVertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).setColor(0.65F, 0.90F, 1.00F, 0.05F * pulse);
        bb.addVertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).setColor(0.65F, 0.90F, 1.00F, 0.05F * pulse);
        bb.addVertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z).setColor(0.65F, 0.90F, 1.00F, 0.05F * pulse);
        bb.addVertex(matrix, (float) p4.x, (float) p4.y, (float) p4.z).setColor(0.65F, 0.90F, 1.00F, 0.05F * pulse);
        // Inner bright core
        Vec3 ir = right.scale(0.45D);
        Vec3 iu = vUp.scale(0.45D);
        Vec3 c1 = center.add(ir).add(iu);
        Vec3 c2 = center.subtract(ir).add(iu);
        Vec3 c3 = center.subtract(ir).subtract(iu);
        Vec3 c4 = center.add(ir).subtract(iu);
        bb.addVertex(matrix, (float) c1.x, (float) c1.y, (float) c1.z).setColor(1.00F, 1.00F, 1.00F, 0.90F * pulse);
        bb.addVertex(matrix, (float) c2.x, (float) c2.y, (float) c2.z).setColor(1.00F, 1.00F, 1.00F, 0.90F * pulse);
        bb.addVertex(matrix, (float) c3.x, (float) c3.y, (float) c3.z).setColor(1.00F, 1.00F, 1.00F, 0.90F * pulse);
        bb.addVertex(matrix, (float) c4.x, (float) c4.y, (float) c4.z).setColor(1.00F, 1.00F, 1.00F, 0.90F * pulse);
    }

    /**
     * Periodically spawn small crackle arcs branching off the beam to sell the
     * "high voltage current" feel. Throttled per-beam by {@link BeamState#lastArcTick}.
     */
    private static void spawnCrackleArcs(Minecraft mc, long now, float partialTick) {
        if (mc.level == null) return;
        for (BeamState s : ACTIVE.values()) {
            if (now - s.lastArcTick < ARC_INTERVAL_TICKS) continue;
            s.lastArcTick = now;
            Vec3 origin = resolveOriginFor(s, mc, partialTick);
            Vec3 axis = s.to.subtract(origin);
            double len = axis.length();
            if (len < 1.0D) continue;
            // 1-2 small arcs per pulse
            int n = 1 + mc.level.random.nextInt(2);
            for (int i = 0; i < n; i++) {
                double t = 0.10D + mc.level.random.nextDouble() * 0.85D;
                Vec3 fromArc = origin.add(axis.scale(t));
                Vec3 randDir = new Vec3(
                        mc.level.random.nextDouble() - 0.5D,
                        mc.level.random.nextDouble() - 0.5D,
                        mc.level.random.nextDouble() - 0.5D);
                if (randDir.lengthSqr() < 1.0E-6D) continue;
                randDir = randDir.normalize().scale(0.4D + mc.level.random.nextDouble() * 0.7D);
                Vec3 toArc = fromArc.add(randDir);
                RailgunArcRenderer.spawnImpactSpark(fromArc, toArc, 14 + mc.level.random.nextInt(8));
            }
        }
    }

    /**
     * Emit the four side faces of a rectangular prism segment whose cross-section
     * is aligned to {@code basis} (a pre-rotated orthonormal pair in the plane
     * perpendicular to the beam axis). Radii may differ at each end for taper.
     * End caps are omitted -- they are hidden by the endpoint glow and the muzzle.
     */
    private static void addPrismSegment(BufferBuilder bb, org.joml.Matrix4f matrix,
                                        Vec3 from, Vec3 to, Vec3[] basis,
                                        float radiusFrom, float radiusTo,
                                        float r, float g, float b,
                                        float aFrom, float aTo) {
        Vec3 u = basis[0];
        Vec3 v = basis[1];
        // Four corners of the square cross-section at each end, in CCW order
        // when viewed from the muzzle end looking toward the impact.
        Vec3 uF = u.scale(radiusFrom);
        Vec3 vF = v.scale(radiusFrom);
        Vec3 uT = u.scale(radiusTo);
        Vec3 vT = v.scale(radiusTo);
        Vec3 f0 = from.add(uF).add(vF);
        Vec3 f1 = from.subtract(uF).add(vF);
        Vec3 f2 = from.subtract(uF).subtract(vF);
        Vec3 f3 = from.add(uF).subtract(vF);
        Vec3 t0 = to.add(uT).add(vT);
        Vec3 t1 = to.subtract(uT).add(vT);
        Vec3 t2 = to.subtract(uT).subtract(vT);
        Vec3 t3 = to.add(uT).subtract(vT);
        emitFace(bb, matrix, f0, f1, t1, t0, r, g, b, aFrom, aTo);
        emitFace(bb, matrix, f1, f2, t2, t1, r, g, b, aFrom, aTo);
        emitFace(bb, matrix, f2, f3, t3, t2, r, g, b, aFrom, aTo);
        emitFace(bb, matrix, f3, f0, t0, t3, r, g, b, aFrom, aTo);
    }

    private static void emitFace(BufferBuilder bb, org.joml.Matrix4f matrix,
                                 Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                 float r, float g, float b,
                                 float aFrom, float aTo) {
        bb.addVertex(matrix, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r, g, b, aFrom);
        bb.addVertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, aFrom);
        bb.addVertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, aTo);
        bb.addVertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, aTo);
    }
}
