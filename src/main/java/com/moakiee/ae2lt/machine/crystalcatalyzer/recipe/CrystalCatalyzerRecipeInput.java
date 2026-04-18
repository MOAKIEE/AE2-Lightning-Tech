package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;

/**
 * Snapshot of the machine state that's fed into {@link CrystalCatalyzerRecipe#matches}.
 *
 * <p>Indices 0/1 expose catalyst and primary stacks; the tank is passed alongside
 * via {@link #fluid()} since {@link RecipeInput} is item-only.</p>
 */
public final class CrystalCatalyzerRecipeInput implements RecipeInput {
    private final ItemStack catalyst;
    private final ItemStack primary;
    private final FluidStack fluid;

    public CrystalCatalyzerRecipeInput(ItemStack catalyst, ItemStack primary, FluidStack fluid) {
        this.catalyst = catalyst == null ? ItemStack.EMPTY : catalyst.copy();
        this.primary = primary == null ? ItemStack.EMPTY : primary.copy();
        this.fluid = fluid == null ? FluidStack.EMPTY : fluid.copy();
    }

    public static CrystalCatalyzerRecipeInput fromMachine(
            CrystalCatalyzerInventory inventory,
            FluidStack fluid) {
        return new CrystalCatalyzerRecipeInput(
                inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST),
                inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_PRIMARY),
                fluid);
    }

    public ItemStack catalyst() {
        return catalyst;
    }

    public ItemStack primary() {
        return primary;
    }

    public FluidStack fluid() {
        return fluid;
    }

    @Override
    public ItemStack getItem(int slotIndex) {
        return switch (slotIndex) {
            case 0 -> catalyst;
            case 1 -> primary;
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public int size() {
        return 2;
    }
}
