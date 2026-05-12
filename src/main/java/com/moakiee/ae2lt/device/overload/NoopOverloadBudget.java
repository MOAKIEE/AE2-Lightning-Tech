package com.moakiee.ae2lt.device.overload;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * {@link OverloadBudget} reserved hook for the railgun. Cap is intentionally
 * {@link Integer#MAX_VALUE} so existing weapon behavior is unchanged — when a
 * future toggle activates overload accounting for weapons, this is the entry
 * point to wire it.
 */
public final class NoopOverloadBudget implements OverloadBudget {

    public static final NoopOverloadBudget INSTANCE = new NoopOverloadBudget();

    private NoopOverloadBudget() {}

    @Override
    public int currentLoad(ItemStack stack) {
        return 0;
    }

    @Override
    public int budgetCap(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public LockState lockState(ItemStack stack) {
        return LockState.UNLOCKED;
    }

    @Override
    public void tick(ItemStack stack, Player player) {
    }
}
