package com.moakiee.ae2lt.overload.provider;

import java.util.List;
import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.model.CompareKey;
import com.moakiee.ae2lt.overload.model.MatchMode;

/**
 * Extraction plan for one logical input slot.
 */
public record PlannedInputSlot(
        int slotIndex,
        ItemStack template,
        long requiredAmount,
        MatchMode matchMode,
        CompareKey compareKey,
        List<PlannedExtraction> extractions
) {
    public PlannedInputSlot {
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
        extractions = List.copyOf(Objects.requireNonNull(extractions, "extractions"));
        template = normalizedCopy(template);
    }

    @Override
    public ItemStack template() {
        return template.copy();
    }

    public long plannedAmount() {
        return extractions.stream().mapToLong(PlannedExtraction::amount).sum();
    }

    public boolean isSatisfied() {
        return plannedAmount() >= requiredAmount;
    }

    private static ItemStack normalizedCopy(ItemStack stack) {
        var copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
