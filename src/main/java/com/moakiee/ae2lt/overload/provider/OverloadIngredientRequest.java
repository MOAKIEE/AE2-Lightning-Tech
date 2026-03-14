package com.moakiee.ae2lt.overload.provider;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.model.CompareKey;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;

/**
 * Provider-side normalized request for one logical input slot.
 */
public record OverloadIngredientRequest(
        int slotIndex,
        ItemStack template,
        long requiredAmount,
        MatchMode matchMode,
        CompareKey compareKey
) {
    public OverloadIngredientRequest {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        Objects.requireNonNull(template, "template");
        if (template.isEmpty()) {
            throw new IllegalArgumentException("template must not be empty");
        }
        if (requiredAmount <= 0) {
            throw new IllegalArgumentException("requiredAmount must be > 0");
        }
        Objects.requireNonNull(matchMode, "matchMode");
        Objects.requireNonNull(compareKey, "compareKey");
        template = normalizedCopy(template);
    }

    @Override
    public ItemStack template() {
        return template.copy();
    }

    public static OverloadIngredientRequest fromInput(OverloadPatternDetails.InputSlot inputSlot, int copies) {
        Objects.requireNonNull(inputSlot, "inputSlot");
        if (copies <= 0) {
            throw new IllegalArgumentException("copies must be > 0");
        }

        return new OverloadIngredientRequest(
                inputSlot.slotIndex(),
                inputSlot.template(),
                (long) inputSlot.amountPerCraft() * copies,
                inputSlot.matchMode(),
                inputSlot.compareKey());
    }

    private static ItemStack normalizedCopy(ItemStack stack) {
        var copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
