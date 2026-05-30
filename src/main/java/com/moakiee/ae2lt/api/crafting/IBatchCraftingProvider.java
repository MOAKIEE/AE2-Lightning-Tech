package com.moakiee.ae2lt.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

/**
 * Optional crafting-provider contract for CPU-side batch dispatch.
 *
 * <p>The AE2 CPU mixin may pre-extract up to {@code maxCraft} homogeneous
 * copies of a pattern's inputs and pass the scaled inputs to this method once.
 * Implementations return how many copies were not accepted; the CPU reinjects
 * that leftover and treats the accepted copy count as if that many vanilla
 * {@link #pushPattern} calls had succeeded.
 */
public interface IBatchCraftingProvider extends ICraftingProvider {
    /**
     * Maximum copies this provider can accept for this pattern right now.
     */
    default int getBatchCapacity(IPatternDetails details) {
        return isBusy() ? 0 : 1;
    }

    /**
     * Try to consume up to {@code maxCraft} copies of {@code details}.
     *
     * @param details the pattern being crafted
     * @param scaledInputs inputs already multiplied by {@code maxCraft}
     * @param maxCraft maximum copies the caller is willing to dispatch
     * @return leftover copy count in {@code [0, maxCraft]}
     */
    int pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, int maxCraft);

    @Override
    default boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return pushBatch(patternDetails, inputHolder, 1) == 0;
    }
}
