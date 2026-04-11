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
    @ModifyArg(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
                    ordinal = 0))
    private Entity ae2lt$markNaturalWeatherLightning(Entity entity) {
        if (entity instanceof LightningBolt lightningBolt) {
            lightningBolt.getPersistentData().putBoolean(
                    NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG,
                    true);
        }
        return entity;
    }
}
