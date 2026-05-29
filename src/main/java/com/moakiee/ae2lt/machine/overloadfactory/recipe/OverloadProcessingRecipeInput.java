package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;

public record OverloadProcessingRecipeInput(List<SlotStack> slotStacks, FluidStack inputFluid) implements Container {
    public static OverloadProcessingRecipeInput fromInventory(
            OverloadProcessingFactoryInventory inventory,
            FluidStack inputFluid) {
        List<SlotStack> slotStacks = new ArrayList<>(OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT);
        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                slotStacks.add(new SlotStack(slot, stack.copy()));
            }
        }
        return new OverloadProcessingRecipeInput(List.copyOf(slotStacks), inputFluid.copy());
    }

    @Override
    public ItemStack getItem(int index) {
        return slotStacks.get(index).stack();
    }

    public boolean hasInput() {
        return !slotStacks.isEmpty() || !inputFluid.isEmpty();
    }

    @Override
    public int getContainerSize() {
        return slotStacks.size();
    }

    @Override
    public boolean isEmpty() {
        return slotStacks.isEmpty();
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

    public record SlotStack(int slot, ItemStack stack) {
        public SlotStack {
            if (slot < OverloadProcessingFactoryInventory.SLOT_INPUT_0
                    || slot > OverloadProcessingFactoryInventory.SLOT_INPUT_8) {
                throw new IllegalArgumentException("slot must be an input slot");
            }
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("stack cannot be empty");
            }
            stack = stack.copy();
        }
    }
}
