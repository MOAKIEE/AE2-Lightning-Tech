package com.moakiee.ae2lt.machine.firmament;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;

/**
 * Four-slot inventory for the Firmament Conversion Core.
 *
 * <p>Slot layout:
 * 0-2 = unordered recipe inputs
 * 3   = output only
 */
public class FirmamentConversionInventory extends LargeStackItemHandler {
    public static final int SLOT_INPUT_0 = 0;
    public static final int SLOT_INPUT_1 = 1;
    public static final int SLOT_INPUT_2 = 2;
    public static final int SLOT_OUTPUT = 3;

    public static final int SLOT_COUNT = 4;
    public static final int SLOT_LIMIT = 64;

    public FirmamentConversionInventory(@Nullable Runnable changeListener) {
        super(SLOT_COUNT, changeListener);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }
        return isInputSlot(slot);
    }

    public boolean isInputSlot(int slot) {
        return slot >= SLOT_INPUT_0 && slot <= SLOT_INPUT_2;
    }

    public ItemStack insertRecipeOutput(ItemStack stack, boolean simulate) {
        return insertItemUnchecked(SLOT_OUTPUT, stack, simulate);
    }

    public boolean canAcceptRecipeOutput(ItemStack stack) {
        return insertRecipeOutput(stack, true).isEmpty();
    }
}
