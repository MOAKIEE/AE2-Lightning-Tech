package com.moakiee.ae2lt.menu;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import net.minecraft.resources.Identifier;

public class IconAppEngSlot extends AppEngSlot {
    private final Identifier noItemIcon;

    public IconAppEngSlot(InternalInventory inventory, int slot, Identifier noItemIcon) {
        super(inventory, slot);
        this.noItemIcon = noItemIcon;
    }

    @Override
    public Identifier getNoItemIcon() {
        return noItemIcon;
    }
}
