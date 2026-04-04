package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.pathing.ChannelMode;
import appeng.me.GridConnection;

@Mixin(GridConnection.class)
public abstract class GridConnectionMaxChannelsMixin {

    @Inject(method = "getMaxChannels", at = @At("HEAD"), cancellable = true)
    private void ae2lt$use128ChannelsForOwnConnections(CallbackInfoReturnable<Integer> cir) {
        var self = (GridConnection) (Object) this;

        // AE2 1.21.1 exposes GridConnection#a()/b() for the two endpoint nodes.
        // If your target AE2/MC version renames these methods, verify them first.
        var ownerA = OverloadedChannelOwnerHelper.tryGetOwner(self.a());
        var ownerB = OverloadedChannelOwnerHelper.tryGetOwner(self.b());

        // Owner-scoped guard:
        // This does NOT affect vanilla AE2 connections because we only return
        // early if BOTH endpoint owners are the explicit AE2LT overloaded types.
        if (!OverloadedChannelOwnerHelper.is128ChannelConnection(ownerA, ownerB)) {
            return;
        }

        var channelMode = self.b().getGrid().getPathingService().getChannelMode();
        if (channelMode == ChannelMode.INFINITE) {
            // Preserve AE2's original infinite-mode semantics.
            return;
        }

        cir.setReturnValue(Integer.MAX_VALUE / 2);
    }
}
