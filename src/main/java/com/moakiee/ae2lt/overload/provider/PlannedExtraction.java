package com.moakiee.ae2lt.overload.provider;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;

/**
 * One exact extraction slice inside an overload input plan.
 * <p>
 * For ID_ONLY requests, multiple planned extractions may exist for the same
 * logical slot because the provider may need to combine several exact NBT
 * variants to satisfy one slot.
 */
public record PlannedExtraction(
        int slotIndex,
        AEKey storageKey,
        ItemStack stack,
        long amount
) {
    public PlannedExtraction {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("stack must not be empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        stack = normalizedCopy(stack);
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    private static ItemStack normalizedCopy(ItemStack stack) {
        var copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
