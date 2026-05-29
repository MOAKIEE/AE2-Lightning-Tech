package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.event.LightningItemTransformationHandler;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void ae2lt$onItemTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        LightningItemTransformationHandler.onItemTick(self);
    }
}
