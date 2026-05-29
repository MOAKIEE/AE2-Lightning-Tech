package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.event.LightningItemTransformationHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityInvulnerableMixin {
    @Inject(method = "isInvulnerableTo", at = @At("RETURN"), cancellable = true)
    private void ae2lt$onInvulnerabilityCheck(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ItemEntity itemEntity && LightningItemTransformationHandler.shouldIgnoreDamage(itemEntity, source)) {
            cir.setReturnValue(true);
        }
    }
}
