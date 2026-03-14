package com.moakiee.ae2lt.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.helpers.patternprovider.PatternProviderLogic;

import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

/**
 * Keeps overload patterns out of normal AE2 pattern providers.
 * <p>
 * OverloadedPatternProviderLogic rebuilds its pattern list separately and still
 * accepts these patterns.
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {
    @Redirect(
            method = "updatePatterns",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/crafting/PatternDetailsHelper;decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;"
            ),
            remap = false
    )
    private @Nullable IPatternDetails ae2lt$rejectOverloadPatternsInNormalProviders(ItemStack stack, Level level) {
        var details = PatternDetailsHelper.decodePattern(stack, level);
        return details instanceof OverloadedProviderOnlyPatternDetails ? null : details;
    }
}
