package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/**
 * Shared conversions between AE2 pattern details and overload-specific model
 * types.
 */
public final class OverloadPatternSupport {
    private OverloadPatternSupport() {
    }

    public static ParsedPatternDefinition toParsedDefinition(ItemStack sourcePatternStack, IPatternDetails sourceDetails) {
        Objects.requireNonNull(sourcePatternStack, "sourcePatternStack");
        Objects.requireNonNull(sourceDetails, "sourceDetails");

        var builder = ParsedPatternDefinition.builder(sourcePatternStack);

        var inputs = sourceDetails.getInputs();
        for (int slot = 0; slot < inputs.length; slot++) {
            builder.input(slot, firstItemTemplate(inputs[slot].getPossibleInputs()));
        }

        var outputs = sourceDetails.getOutputs();
        for (int slot = 0; slot < outputs.size(); slot++) {
            builder.output(slot, toItemStack(outputs.get(slot)), slot == 0);
        }

        return builder.build();
    }

    public static ItemStack toItemStack(GenericStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            throw new IllegalArgumentException("overload patterns currently only support item stacks");
        }
        return itemKey.toStack((int) stack.amount());
    }

    private static ItemStack firstItemTemplate(GenericStack[] possibleInputs) {
        for (var possible : possibleInputs) {
            if (possible.what() instanceof AEItemKey itemKey) {
                return itemKey.toStack((int) possible.amount());
            }
        }
        throw new IllegalArgumentException("overload patterns currently only support item inputs");
    }
}
