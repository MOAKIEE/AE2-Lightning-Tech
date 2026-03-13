package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.GridFlags;
import appeng.api.networking.pathing.ChannelMode;
import appeng.me.GridNode;

@Mixin(GridNode.class)
public abstract class GridNodeMaxChannelsMixin {

    @Inject(method = "getMaxChannels", at = @At("HEAD"), cancellable = true)
    private void ae2lt$use128ChannelsForOwnOwners(CallbackInfoReturnable<Integer> cir) {
        var self = (GridNode) (Object) this;
        var owner = OverloadedChannelOwnerHelper.tryGetOwner(self);

        // Owner-scoped guard:
        // This does NOT affect vanilla AE2 nodes because we only return early for the
        // two explicit AE2LT owner classes:
        // - OverloadedControllerBlockEntity
        // - OverloadedCablePart
        if (!OverloadedChannelOwnerHelper.is128ChannelOwner(owner)) {
            return;
        }

        if (self.hasFlag(GridFlags.CANNOT_CARRY)) {
            return;
        }

        var channelMode = self.getGrid().getPathingService().getChannelMode();
        if (channelMode == ChannelMode.INFINITE) {
            // Preserve AE2's original infinite-mode semantics.
            return;
        }

        // In AE2 1.21.1 this method is GridNode#getMaxChannels().
        // If mappings or method names differ in another version, verify this target first.
        cir.setReturnValue(128 * channelMode.getCableCapacityFactor());
    }
}
