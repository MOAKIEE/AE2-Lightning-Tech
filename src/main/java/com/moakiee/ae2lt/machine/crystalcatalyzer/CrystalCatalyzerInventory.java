package com.moakiee.ae2lt.machine.crystalcatalyzer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.registry.ModItems;

/**
 * Crystal Catalyzer machine inventory.
 *
 * <p>Slot layout:
 * 0 = catalyst (1024)
 * 1 = lightning collapse matrix (1)
 * 2 = output (1024, machine-write only)</p>
 */
public class CrystalCatalyzerInventory extends LargeStackItemHandler {
    public static final int SLOT_CATALYST = 0;
    public static final int SLOT_MATRIX = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    public static final int LARGE_SLOT_LIMIT = 1024;
    public static final int MATRIX_SLOT_LIMIT = 1;

    @Nullable
    private Level level;

    public CrystalCatalyzerInventory(@Nullable Runnable changeListener) {
        super(SLOT_COUNT, changeListener);
    }

    public void setLevel(@Nullable Level level) {
        this.level = level;
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return slot == SLOT_MATRIX ? MATRIX_SLOT_LIMIT : LARGE_SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }

        return switch (slot) {
            case SLOT_MATRIX -> isLightningCollapseMatrix(stack);
            case SLOT_CATALYST -> CrystalCatalyzerRecipeService.isKnownCatalyst(level, stack);
            case SLOT_OUTPUT -> false;
            default -> false;
        };
    }

    public boolean isLightningCollapseMatrix(ItemStack stack) {
        return stack.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get());
    }

    public boolean hasLightningCollapseMatrix() {
        return isLightningCollapseMatrix(getStackInSlot(SLOT_MATRIX));
    }

    public ItemStack insertRecipeOutput(ItemStack stack, boolean simulate) {
        return insertItemUnchecked(SLOT_OUTPUT, stack, simulate);
    }

    public boolean canAcceptRecipeOutput(ItemStack stack) {
        return insertRecipeOutput(stack, true).isEmpty();
    }

    public void setClientRenderStack(int slot, ItemStack stack) {
        setStackInSlotUnchecked(slot, stack);
    }
}
