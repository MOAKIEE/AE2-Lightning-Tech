package com.moakiee.ae2lt.menu;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;

/**
 * Menu slot that honors the backing inventory's slot limit even when it
 * exceeds the item's vanilla max stack size.
 */
public class LargeStackAppEngSlot extends AppEngSlot {
    private final Identifier noItemIcon;

    public LargeStackAppEngSlot(InternalInventory inventory, int slot) {
        this(inventory, slot, null);
    }

    public LargeStackAppEngSlot(InternalInventory inventory, int slot, Identifier noItemIcon) {
        super(inventory, slot);
        this.noItemIcon = noItemIcon;
        setHideAmount(true);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return getMaxStackSize();
    }

    @Override
    public Identifier getNoItemIcon() {
        return noItemIcon != null ? noItemIcon : super.getNoItemIcon();
    }
}
