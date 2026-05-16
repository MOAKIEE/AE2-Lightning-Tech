package com.moakiee.ae2lt.item.railgun;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.overload.LockState;
import com.moakiee.ae2lt.device.overload.OverloadBudget;

public final class RailgunOverloadBudget implements OverloadBudget {
    public static final RailgunOverloadBudget INSTANCE = new RailgunOverloadBudget();

    private RailgunOverloadBudget() {}

    @Override
    public int currentLoad(ItemStack stack) {
        return RailgunModuleStorage.currentIdleOverload(stack);
    }

    @Override
    public int budgetCap(ItemStack stack) {
        return RailgunModuleStorage.baseOverloadBudget(stack);
    }

    @Override
    public LockState lockState(ItemStack stack) {
        return LockState.UNLOCKED;
    }

    @Override
    public void tick(ItemStack stack, Player player) {
    }
}
