package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.network.railgun.RailgunFirePacket;

/**
 * Plays charged-fire client effects:
 *  - real electric arcs along each chain segment (via {@link RailgunArcRenderer})
 *  - a ground-aligned expanding shockwave + flash core at the impact point (via
 *    {@link RailgunShockwaveRenderer})
 *  - radial mini-bolts crackling outward from the impact
 *  - layered vanilla particles (FLASH, ELECTRIC_SPARK, LARGE_SMOKE) for grit
 *  - tier-scaled thunder sound
 *
 * <p>All lifetimes are tuned to feel deliberate (≈1.5–2.5 s on the heavier
 * effects). The plasma trail's origin is the shooter's gun barrel (resolved via
 * {@link RailgunVisuals}), not the screen-center / eye, so the visual reads as
 * "fired from the weapon".
 */
public final class RailgunClientFx {

    private RailgunClientFx() {}

    public static void playCharged(RailgunFirePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 hit = p.firstHit();
        boolean isMax = p.isMax();
        int tier = Math.max(1, p.tier());

        // The plasma trail visually emanates from the firing player's gun barrel.
        // Server-supplied {@code from} is the eye position; we use it only as fallback.
        // Use the current frame's partial tick so the barrel is interpolated to the same
        // moment as the rest of the rendering — matters most for the local player whose
        // pose may have advanced past the tick boundary by the time the packet arrives.
        Vec3 plasmaOrigin = RailgunVisuals.resolveShooterBarrel(
                p.shooterId(), p.from(), RailgunVisuals.currentPartialTick());

        // 1. Shockwave + flash core at the impact point (slow, deliberate ease-out).
        float radius = p.impactRadius();
        if (radius > 0.0F) {
            int waveLife = isMax ? 80 : 56 + tier * 6;
            RailgunShockwaveRenderer.spawn(hit, radius, waveLife);
            // Faster inner ring for a layered "double pop" feel.
            RailgunShockwaveRenderer.spawn(hit, radius * 0.50F, Math.max(35, waveLife - 15));
        }

        // 2. Chain arcs — render real lightning along each (from -> to) pair.
        var path = p.chainPath();
        for (int i = 0; i + 1 < path.size(); i += 2) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            int chainLife = isMax ? 40 : 32;
            RailgunArcRenderer.spawnChain(a, b, chainLife);
            // Endpoint sparks for each chain target.
            for (int s = 0; s < 4; s++) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z,
                        (mc.level.random.nextDouble() - 0.5) * 0.4,
                        (mc.level.random.nextDouble() - 0.5) * 0.4,
                        (mc.level.random.nextDouble() - 0.5) * 0.4);
            }
        }

        // 3. Plasma trail from gun barrel to impact — the headline "shot" effect.
        // Slightly thinner spread + longer lifetime than the chain arcs so it
        // reads as the main projectile path.
        RailgunArcRenderer.spawnPlasma(plasmaOrigin, hit,
                Math.max(10, (int) Math.round(plasmaOrigin.distanceTo(hit) * 1.2D)),
                isMax ? 0.30F : 0.18F,
                isMax ? 36 : 30);

        // 4. Radial mini-bolts crackling outward from the impact (longer-lived now).
        if (radius > 0.0F) {
            int bolts = isMax ? 18 : 8 + tier * 2;
            var rng = mc.level.random;
            for (int i = 0; i < bolts; i++) {
                double yaw = rng.nextDouble() * Math.PI * 2.0D;
                double pitch = (rng.nextDouble() - 0.3D) * Math.PI;       // bias upward
                double r = radius * (0.6F + rng.nextFloat() * 0.6F);
                double dx = Math.cos(yaw) * Math.cos(pitch) * r;
                double dy = Math.sin(pitch) * r;
                double dz = Math.sin(yaw) * Math.cos(pitch) * r;
                Vec3 end = hit.add(dx, dy, dz);
                RailgunArcRenderer.spawnImpactSpark(hit, end, 22 + rng.nextInt(10));
            }
        }

        // 5. Layered vanilla particles for grit + dust.
        // Scale spread/counts by radius — the central FLASH/smoke/spark cloud
        // has fixed offsets historically, which now reads small inside the bigger
        // shockwave ring. radiusScale = radius / 7 (the old tier-3 default).
        float radiusScale = Math.max(1.0F, radius / 7.0F);
        mc.level.addParticle(ParticleTypes.FLASH, hit.x, hit.y, hit.z, 0, 0, 0);
        if (isMax) {
            int flashCount = (int) (4 * radiusScale);
            float flashSpread = 0.7F * radiusScale;
            for (int i = 0; i < flashCount; i++) {
                double ox = (mc.level.random.nextDouble() - 0.5D) * flashSpread;
                double oy = (mc.level.random.nextDouble() - 0.5D) * flashSpread;
                double oz = (mc.level.random.nextDouble() - 0.5D) * flashSpread;
                mc.level.addParticle(ParticleTypes.FLASH, hit.x + ox, hit.y + oy, hit.z + oz, 0, 0, 0);
            }
            int smokeCount = (int) (48 * radiusScale);
            double smokeMotion = 0.30D * radiusScale;
            double smokeUp = 0.25D * radiusScale;
            for (int i = 0; i < smokeCount; i++) {
                mc.level.addParticle(ParticleTypes.LARGE_SMOKE,
                        hit.x, hit.y, hit.z,
                        (mc.level.random.nextDouble() - 0.5D) * smokeMotion,
                        (mc.level.random.nextDouble() - 0.2D) * smokeUp,
                        (mc.level.random.nextDouble() - 0.5D) * smokeMotion);
            }
        }
        int sparkCount = (int) ((14 + tier * 8) * radiusScale);
        double sparkVel = (0.4D + tier * 0.18D) * radiusScale;
        for (int i = 0; i < sparkCount; i++) {
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    hit.x, hit.y, hit.z,
                    (mc.level.random.nextDouble() - 0.5D) * sparkVel,
                    (mc.level.random.nextDouble() - 0.5D) * sparkVel,
                    (mc.level.random.nextDouble() - 0.5D) * sparkVel);
        }

        // 6. Sound: thunder for max, amethyst chime for sub-tiers, both spatial.
        var sound = isMax ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.AMETHYST_BLOCK_CHIME;
        mc.level.playLocalSound(hit.x, hit.y, hit.z, sound, SoundSource.PLAYERS,
                isMax ? 1.7f : 0.9f + 0.15f * tier, 1.0f, false);
        if (isMax) {
            mc.level.playLocalSound(hit.x, hit.y, hit.z, SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 1.4f, 0.7f, false);
        }
    }
}
