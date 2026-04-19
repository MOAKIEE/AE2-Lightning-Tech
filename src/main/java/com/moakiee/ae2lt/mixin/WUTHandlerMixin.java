package com.moakiee.ae2lt.mixin;

import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.menu.locator.ItemMenuHostLocator;

import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;

import com.moakiee.ae2lt.overload.armor.OverloadArmorTerminalService;

@Mixin(value = WUTHandler.class, remap = false)
public abstract class WUTHandlerMixin {
    @Inject(method = "findTerminal", at = @At("RETURN"), cancellable = true)
    private static void ae2lt$findArmorTerminal(
            Player player,
            WTDefinition terminalDefinition,
            CallbackInfoReturnable<ItemMenuHostLocator> cir
    ) {
        if (cir.getReturnValue() != null) {
            return;
        }

        var locator = OverloadArmorTerminalService.findTerminalLocator(player, terminalDefinition);
        if (locator != null) {
            cir.setReturnValue(locator);
        }
    }
}
