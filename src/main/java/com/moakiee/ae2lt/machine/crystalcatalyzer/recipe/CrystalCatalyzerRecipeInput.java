package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;

/**
 * Snapshot of the machine state that's fed into {@link CrystalCatalyzerRecipe#matches}.
 *
 * <p>Index 0 exposes the catalyst stack; the tank is passed alongside via
 * {@link #fluid()} since {@link RecipeInput} is item-only.</p>
 */
public final class CrystalCatalyzerRecipeInput implements RecipeInput {
    private final ItemStack catalyst;
    private final FluidStack fluid;

    public CrystalCatalyzerRecipeInput(ItemStack catalyst, FluidStack fluid) {
        this.catalyst = catalyst == null ? ItemStack.EMPTY : catalyst.copy();
        this.fluid = fluid == null ? FluidStack.EMPTY : fluid.copy();
    }

    public static CrystalCatalyzerRecipeInput fromMachine(
            CrystalCatalyzerInventory inventory,
            FluidStack fluid) {
        return new CrystalCatalyzerRecipeInput(
                inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST),
                fluid);
    }

    public ItemStack catalyst() {
        return catalyst;
    }

    public FluidStack fluid() {
        return fluid;
    }

    @Override
    public ItemStack getItem(int slotIndex) {
        return slotIndex == 0 ? catalyst : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1;
    }
}
