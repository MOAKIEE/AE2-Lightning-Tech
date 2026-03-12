package com.moakiee.ae2lt.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;

/**
 * Mixin accessor exposing private members of {@code PatternProviderLogic}
 * needed by the wireless dispatch path in {@code OverloadedPatternProviderLogic}.
 */
@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {

    @Invoker("onPushPatternSuccess")
    void invokeOnPushPatternSuccess(IPatternDetails pattern);

    /** The union of all possible pattern inputs (keys with secondary dropped). */
    @Accessor("patternInputs")
    Set<AEKey> getPatternInputs();
}
