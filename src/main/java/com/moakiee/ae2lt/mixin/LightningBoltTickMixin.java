package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.event.LightningItemTransformationHandler;
import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public class LightningBoltTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2lt$onLightningTick(CallbackInfo ci) {
        LightningBolt self = (LightningBolt) (Object) this;
        if (self.level() instanceof ServerLevel serverLevel) {
            LightningItemTransformationHandler.onLightningTick(self, serverLevel);
            NaturalLightningTransformationHandler.onLightningTick(self, serverLevel);
        }
    }
}
