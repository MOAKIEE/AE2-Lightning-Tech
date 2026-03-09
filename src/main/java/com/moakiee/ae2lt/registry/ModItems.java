package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2LightningTech.MODID);

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL =
            ITEMS.registerSimpleItem("overload_crystal", new Item.Properties());

    private ModItems() {
    }
}
