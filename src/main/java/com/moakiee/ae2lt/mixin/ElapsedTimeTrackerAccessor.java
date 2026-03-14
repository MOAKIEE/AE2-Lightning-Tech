package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;

@Mixin(ElapsedTimeTracker.class)
public interface ElapsedTimeTrackerAccessor {
    @Invoker("decrementItems")
    void invokeDecrementItems(long amount, AEKeyType keyType);
}
