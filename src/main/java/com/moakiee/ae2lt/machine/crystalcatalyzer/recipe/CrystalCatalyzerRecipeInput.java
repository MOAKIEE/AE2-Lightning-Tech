package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;

/**
 * Snapshot of the machine state that's fed into {@link CrystalCatalyzerRecipe#matches}.
 *
 * <p>Only the catalyst slot is relevant now — the fluid requirement is no longer
 * part of the recipe; the machine drains a fixed water cost per cycle independent
 * of the selected recipe.</p>
 */
public final class CrystalCatalyzerRecipeInput implements Container {
    private final ItemStack catalyst;

    public CrystalCatalyzerRecipeInput(ItemStack catalyst) {
        this.catalyst = catalyst == null ? ItemStack.EMPTY : catalyst.copy();
    }

    public static CrystalCatalyzerRecipeInput fromMachine(CrystalCatalyzerInventory inventory) {
        return new CrystalCatalyzerRecipeInput(
                inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST));
    }

    public ItemStack catalyst() {
        return catalyst;
    }

    @Override
    public ItemStack getItem(int slotIndex) {
        return slotIndex == 0 ? catalyst : ItemStack.EMPTY;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return catalyst.isEmpty();
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
    }
}
