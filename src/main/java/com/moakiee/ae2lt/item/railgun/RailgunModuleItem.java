package com.moakiee.ae2lt.item.railgun;

import net.minecraft.world.item.Item;

public class RailgunModuleItem extends Item {
    private final RailgunModuleType type;

    public RailgunModuleItem(Properties properties, RailgunModuleType type) {
        super(properties);
        this.type = type;
    }

    public RailgunModuleType moduleType() {
        return type;
    }
}
