package com.moakiee.ae2lt.machine.teslacoil;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class TeslaCoilAutomationInventory implements IItemHandlerModifiable {
    private final TeslaCoilInventory inventory;

    public TeslaCoilAutomationInventory(TeslaCoilInventory inventory) {
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
        Objects.requireNonNull(stack, "stack");

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (inventory.isOverloadCrystalDust(stack)) {
            return inventory.insertItem(TeslaCoilInventory.SLOT_DUST, stack, simulate);
        }

        if (inventory.isLightningCollapseMatrix(stack)) {
            return inventory.insertItem(TeslaCoilInventory.SLOT_MATRIX, stack, simulate);
        }

        return stack;
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
        return inventory.isItemValid(slot, stack);
    }
}
