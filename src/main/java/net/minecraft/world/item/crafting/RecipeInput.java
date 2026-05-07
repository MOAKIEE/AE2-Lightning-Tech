package net.minecraft.world.item.crafting;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal 1.21 recipe-input shim backed by the 1.20.1 container contract.
 */
public interface RecipeInput extends Container {
    int size();

    @Override
    default int getContainerSize() {
        return size();
    }

    @Override
    default ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    default void setItem(int slot, ItemStack stack) {
    }

    @Override
    default void setChanged() {
    }

    @Override
    default boolean stillValid(Player player) {
        return true;
    }

    @Override
    default void clearContent() {
    }
}
