package com.moakiee.ae2lt.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Electromagnetic Paralysis: -50% movement speed (precise, via attribute modifier),
 * 1 second by default. Independent of vanilla MOVEMENT_SLOWDOWN so they can stack.
 *
 * <p>The exact -50% multiplier is achieved via {@code addAttributeModifier} on
 * {@link net.minecraft.world.entity.ai.attributes.Attributes#MOVEMENT_SPEED}
 * with operation {@code ADD_MULTIPLIED_TOTAL} and value {@code -0.5}.
 * Vanilla SLOWNESS uses 0.15 per amplifier which can't hit -50% exactly.
 */
public class ElectromagneticParalysisEffect extends MobEffect {
    public ElectromagneticParalysisEffect() {
        super(MobEffectCategory.HARMFUL, 0x88BBFF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Spawn sparks every 5 ticks for visual feedback.
        return duration % 5 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            double w = entity.getBbWidth() * 0.4;
            double h = entity.getBbHeight() * 0.4;
            level.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    entity.getX(),
                    entity.getY() + entity.getBbHeight() / 2.0,
                    entity.getZ(),
                    2,
                    w, h, w, 0.05);
        }
        return true;
    }
}
