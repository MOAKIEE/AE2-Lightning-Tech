package com.moakiee.ae2lt.machine.lightningchamber;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>Important behavior:
 * catalyst matrices are always routed to slot 3 first, even when an
 * automation helper starts probing slots from 0 upward.</p>
 */
public class LightningSimulationChamberAutomationInventory implements IItemHandlerModifiable {
    private final LightningSimulationChamberInventory inventory;

    public LightningSimulationChamberAutomationInventory(LightningSimulationChamberInventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public int getSlots() {
        return inventory.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        inventory.validateSlotIndex(slot);
        Objects.requireNonNull(stack, "stack");

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (inventory.isCatalystItem(stack)) {
            return inventory.insertItem(LightningSimulationChamberInventory.SLOT_CATALYST, stack, simulate);
        }

        if (inventory.isInputSlot(slot)) {
            return inventory.insertItem(slot, stack, simulate);
        }

        return stack;
    }

    /**
     * Convenience path for machine-adjacent automation that wants "best effort"
     * insertion instead of targeting a particular physical slot.
     */
    public ItemStack insertItem(ItemStack stack, boolean simulate) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (inventory.isCatalystItem(stack)) {
            return inventory.insertItem(LightningSimulationChamberInventory.SLOT_CATALYST, stack, simulate);
        }

        ItemStack remainder = stack;
        for (int slot = LightningSimulationChamberInventory.SLOT_INPUT_0;
             slot <= LightningSimulationChamberInventory.SLOT_INPUT_2;
             slot++) {
            remainder = inventory.insertItem(slot, remainder, simulate);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (inventory.isCatalystItem(stack)) {
            return slot != LightningSimulationChamberInventory.SLOT_OUTPUT;
        }

        return inventory.isInputSlot(slot);
    }
}
