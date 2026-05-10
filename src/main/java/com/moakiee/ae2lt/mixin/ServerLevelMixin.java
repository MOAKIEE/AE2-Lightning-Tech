package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    // Intercept addFreshEntity to tag lightning bolts spawned by natural weather.
    // In 26.1.2, natural weather lightning is spawned from ServerLevel#tickThunder.
    @Inject(
            method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"))
    private void ae2lt$tagNaturalWeatherLightning(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof LightningBolt lightningBolt)) {
            return;
        }

        // Check if this call comes from tickThunder by examining the stack trace
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            if ("tickThunder".equals(element.getMethodName())
                    && element.getClassName().contains("ServerLevel")) {
                lightningBolt.getPersistentData().putBoolean(
                        NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG,
                        true);
                return;
            }
        }
    }
}
