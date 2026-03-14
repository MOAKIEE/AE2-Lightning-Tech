package com.moakiee.ae2lt.overload.provider;

import java.util.List;
import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.model.MatchMode;

/**
 * Concrete provider-side payload for one logical input slot after extraction.
 */
public record PayloadInputSlot(
        int slotIndex,
        ItemStack template,
        long requestedAmount,
        MatchMode matchMode,
        List<ExtractedPayloadStack> extractedStacks
) {
    public PayloadInputSlot {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        Objects.requireNonNull(template, "template");
        if (template.isEmpty()) {
            throw new IllegalArgumentException("template must not be empty");
        }
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("requestedAmount must be > 0");
        }
        Objects.requireNonNull(matchMode, "matchMode");
        extractedStacks = List.copyOf(Objects.requireNonNull(extractedStacks, "extractedStacks"));
        template = normalizedCopy(template);
    }

    @Override
    public ItemStack template() {
        return template.copy();
    }

    public long extractedAmount() {
        return extractedStacks.stream().mapToLong(ExtractedPayloadStack::amount).sum();
    }

    private static ItemStack normalizedCopy(ItemStack stack) {
        var copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
