package com.moakiee.ae2lt.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;

/**
 * Mixin accessor exposing private members of {@code PatternProviderLogic}
 * needed by the wireless dispatch path in {@code OverloadedPatternProviderLogic}.
 */
@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {

    @Invoker("onPushPatternSuccess")
    void invokeOnPushPatternSuccess(IPatternDetails pattern);

    @Accessor("mainNode")
    IManagedGridNode getMainNode();

    @Accessor("patternInventory")
    AppEngInternalInventory getPatternInventory();

    @Accessor("patterns")
    List<IPatternDetails> getPatterns();

    /** The union of all possible pattern inputs (keys with secondary dropped). */
    @Accessor("patternInputs")
    Set<AEKey> getPatternInputs();
}
