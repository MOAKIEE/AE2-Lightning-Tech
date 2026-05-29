package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import net.minecraftforge.fml.ModList;

/**
 * Compatibility layer for AdvancedAE directional pattern support.
 */
public final class AdvancedAECompat {
    private static final String MOD_ID = "advanced_ae";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private AdvancedAECompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isDirectional(IPatternDetails pattern) {
        if (!LOADED) return false;
        return pattern instanceof net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails adv
                && adv.directionalInputsSet();
    }

    @Nullable
    public static Direction getDirectionForKey(IPatternDetails pattern, AEKey key) {
        if (!LOADED) return null;
        if (pattern instanceof net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails adv) {
            return adv.getDirectionSideForInputKey(key);
        }
        return null;
    }
}
