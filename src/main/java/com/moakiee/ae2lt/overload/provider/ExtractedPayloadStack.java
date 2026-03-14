package com.moakiee.ae2lt.overload.provider;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;

/**
 * One concrete extracted stack that now belongs to the provider-side payload.
 * <p>
 * This is more than a convenience list: it is the exact ledger needed to know
 * what was really removed from network storage and what must be returned if the
 * machine refuses or partially overflows.
 */
public record ExtractedPayloadStack(
        int slotIndex,
        AEKey storageKey,
        ItemStack stack,
        long amount
) {
    public ExtractedPayloadStack {
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
        stack = stack.copy();
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }
}
