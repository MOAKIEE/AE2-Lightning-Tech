package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    // In 26.1.2, weather system was refactored. Try multiple possible method names.
    @ModifyArg(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
                    ordinal = 1),
            require = 0)  // Don't fail if method not found
    private Entity ae2lt$markNaturalWeatherLightning_tickChunk(Entity entity) {
        if (entity instanceof LightningBolt lightningBolt) {
            lightningBolt.getPersistentData().putBoolean(
                    NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG,
                    true);
        }
        return entity;
    }

    // Alternative: try tick method
    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 0)
    private Entity ae2lt$markNaturalWeatherLightning_tick(Entity entity) {
        if (entity instanceof LightningBolt lightningBolt) {
            lightningBolt.getPersistentData().putBoolean(
                    NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG,
                    true);
        }
        return entity;
    }
}
