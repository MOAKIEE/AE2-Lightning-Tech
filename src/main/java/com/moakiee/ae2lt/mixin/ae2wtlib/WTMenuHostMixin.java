package com.moakiee.ae2lt.mixin.ae2wtlib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ILinkStatus;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;

import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

@Mixin(WTMenuHost.class)
public abstract class WTMenuHostMixin {

    @Inject(method = "getActionableNode", at = @At("HEAD"), cancellable = true)
    private void ae2lt$redirectToFrequencyNode(CallbackInfoReturnable<IGridNode> cir) {
        IGridNode node = ae2lt$resolveFrequencyNode();
        if (node != null) {
            cir.setReturnValue(node);
        }
    }

    @Inject(method = "getLinkStatus", at = @At("HEAD"), cancellable = true)
    private void ae2lt$frequencyLinkStatus(CallbackInfoReturnable<ILinkStatus> cir) {
        WTMenuHost self = (WTMenuHost) (Object) this;
        if (self.getPlayer().level().isClientSide()) {
            return;
        }
        if (ae2lt$boundFrequencyId() <= 0) {
            return;
        }
        IGridNode node = ae2lt$resolveFrequencyNode();
        if (node == null) {
            return;
        }
        IGrid grid = node.getGrid();
        if (grid != null && grid.getEnergyService().isNetworkPowered()) {
            cir.setReturnValue(ILinkStatus.ofConnected());
        }
    }

    @Unique
    private IGridNode ae2lt$resolveFrequencyNode() {
        WTMenuHost self = (WTMenuHost) (Object) this;
        if (!(self.getPlayer().level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        int freqId = ae2lt$boundFrequencyId();
        if (freqId <= 0) {
            return null;
        }
        var manager = WirelessFrequencyManager.get();
        if (manager == null) {
            return null;
        }
        return manager.resolveAdvancedNode(freqId, serverLevel.getServer());
    }

    @Unique
    private int ae2lt$boundFrequencyId() {
        WTMenuHost self = (WTMenuHost) (Object) this;
        var upgrades = self.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            var card = upgrades.getStackInSlot(i);
            if (card.getItem() instanceof OverloadedFrequencyCardItem) {
                var data = OverloadedFrequencyCardItem.getData(card);
                if (data.isBound()) {
                    return data.frequencyId();
                }
            }
        }
        return -1;
    }
}
