package com.moakiee.ae2lt.me.cell;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.FixedInfiniteCellItem;

public final class FixedInfiniteCellInventory implements StorageCell {

    private final ItemStack stack;
    private final AEKey storedKey;
    private final double idleDrain;

    public FixedInfiniteCellInventory(ItemStack stack, double idleDrain) {
        if (!(stack.getItem() instanceof FixedInfiniteCellItem)) {
            throw new IllegalArgumentException("Cell isn't a fixed infinite cell");
        }
        this.stack = stack;
        this.storedKey = FixedInfiniteCellItem.getEffectiveKey(stack);
        this.idleDrain = idleDrain;
    }

    @Override
    public CellState getStatus() {
        return CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return this.idleDrain;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return storedKey.equals(what) ? amount : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return storedKey.equals(what) ? amount : 0;
    }

    @Override
    public void persist() {
    }

    @Override
    public Component getDescription() {
        return this.stack.getHoverName();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        out.add(storedKey, getInfiniteAmount(storedKey));
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return storedKey.equals(what);
    }

    private static long getInfiniteAmount(AEKey key) {
        return (long) Integer.MAX_VALUE * key.getAmountPerUnit();
    }
}
