package com.moakiee.ae2lt.machine.crystalcatalyzer;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>Automation may insert into the catalyst or matrix slot (the matrix slot
 * only accepts {@link com.moakiee.ae2lt.registry.ModItems#LIGHTNING_COLLAPSE_MATRIX});
 * extraction is restricted to the output slot.</p>
 */
public class CrystalCatalyzerAutomationInventory implements IItemHandlerModifiable {
    private final CrystalCatalyzerInventory inventory;

    public CrystalCatalyzerAutomationInventory(CrystalCatalyzerInventory inventory) {
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

        if (inventory.isLightningCollapseMatrix(stack)) {
            return inventory.insertItem(CrystalCatalyzerInventory.SLOT_MATRIX, stack, simulate);
        }

        if (slot == CrystalCatalyzerInventory.SLOT_CATALYST) {
            return inventory.insertItem(slot, stack, simulate);
        }

        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != CrystalCatalyzerInventory.SLOT_OUTPUT) {
            return ItemStack.EMPTY;
        }
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

        if (inventory.isLightningCollapseMatrix(stack)) {
            return slot == CrystalCatalyzerInventory.SLOT_MATRIX;
        }

        return slot == CrystalCatalyzerInventory.SLOT_CATALYST;
    }
}

