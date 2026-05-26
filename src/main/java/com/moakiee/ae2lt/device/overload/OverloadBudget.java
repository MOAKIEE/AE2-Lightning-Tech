package com.moakiee.ae2lt.device.overload;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface OverloadBudget {

    int currentLoad(ItemStack stack);

    int budgetCap(ItemStack stack);

    LockState lockState(ItemStack stack);

    void tick(ItemStack stack, Player player);

    default void contributeState(ItemStack stack, String key, int loadPerTick) {
    }

    default void clearState(ItemStack stack, String key) {
    }

    default void contributePulse(ItemStack stack, int base) {
    }

    default void contributePulse(ItemStack stack, int base, double decay, int maxTicks) {
    }
}
