package com.moakiee.ae2lt.menu;

import net.minecraft.world.entity.player.Player;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;

import com.moakiee.ae2lt.item.OverloadArmorItem;
import com.moakiee.ae2lt.overload.armor.OverloadArmorMenuLocator;

public final class OverloadArmorHost extends ItemMenuHost<OverloadArmorItem> {
    private final Player player;
    private final ItemMenuHostLocator locator;

    public OverloadArmorHost(OverloadArmorItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);
        this.player = player;
        this.locator = locator;
    }

    public ItemMenuHostLocator getLocator() {
        return locator;
    }

    public boolean isEquippedCarrier() {
        return locator instanceof OverloadArmorMenuLocator menuLocator && menuLocator.isEquipped(player);
    }
}
