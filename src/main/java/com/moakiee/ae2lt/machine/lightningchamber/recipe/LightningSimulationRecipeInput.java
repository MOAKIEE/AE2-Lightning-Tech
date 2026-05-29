package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;

public final class LightningSimulationRecipeInput implements Container {
    private final List<SlotStack> slotStacks;
    private final List<ItemStack> displayStacks;

    private LightningSimulationRecipeInput(List<SlotStack> slotStacks) {
        this.slotStacks = List.copyOf(slotStacks);
        this.displayStacks = this.slotStacks.stream()
                .map(SlotStack::stack)
                .toList();
    }

    public static LightningSimulationRecipeInput fromInventory(LightningSimulationChamberInventory inventory) {
        List<SlotStack> slotStacks = new ArrayList<>(3);
        for (int slot = LightningSimulationChamberInventory.SLOT_INPUT_0;
             slot <= LightningSimulationChamberInventory.SLOT_INPUT_2;
             slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                slotStacks.add(new SlotStack(slot, stack.copy()));
            }
        }
        return new LightningSimulationRecipeInput(slotStacks);
    }

    public List<SlotStack> slotStacks() {
        return slotStacks;
    }

    @Override
    public ItemStack getItem(int index) {
        return displayStacks.get(index);
    }

    @Override
    public int getContainerSize() {
        return displayStacks.size();
    }

    @Override
    public boolean isEmpty() {
        return displayStacks.isEmpty();
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
            if (slot < LightningSimulationChamberInventory.SLOT_INPUT_0
                    || slot > LightningSimulationChamberInventory.SLOT_INPUT_2) {
                throw new IllegalArgumentException("slot must be one of the three input slots");
            }
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("stack cannot be empty");
            }
            stack = stack.copy();
        }
    }
}
