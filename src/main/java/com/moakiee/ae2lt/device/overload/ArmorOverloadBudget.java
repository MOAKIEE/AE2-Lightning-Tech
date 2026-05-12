package com.moakiee.ae2lt.device.overload;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

/**
 * {@link OverloadBudget} backed by the armor's existing snapshot machinery.
 *
 * <p>Read-side only — the source of truth remains
 * {@link OverloadArmorState#tickEquipped} which is called from the armor item's
 * tick. This wrapper lets device-layer code query budget state through a unified
 * interface; mutation (debt accounting, lock transitions) still happens inside
 * the existing tickEquipped path.
 */
public final class ArmorOverloadBudget implements OverloadBudget {

    public static final ArmorOverloadBudget INSTANCE = new ArmorOverloadBudget();

    private ArmorOverloadBudget() {}

    @Override
    public int currentLoad(ItemStack stack) {
        return OverloadArmorState.snapshot(stack, null, true).currentLoad();
    }

    @Override
    public int budgetCap(ItemStack stack) {
        return OverloadArmorState.snapshot(stack, null, true).baseOverload();
    }

    @Override
    public LockState lockState(ItemStack stack) {
        var snap = OverloadArmorState.snapshot(stack, null, true);
        if (snap.lockedTicks() > 0) return LockState.locked(snap.lockedTicks());
        if (snap.debtTicks() > 0) return LockState.debt(snap.debtTicks());
        return LockState.UNLOCKED;
    }

    @Override
    public void tick(ItemStack stack, Player player) {
        // Mutation happens in OverloadArmorState.tickEquipped from the armor item's tick.
    }
}
