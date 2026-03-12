package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.OverloadCrystalItem;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2LightningTech.MODID);

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL = ITEMS.registerItem(
            "overload_crystal",
            OverloadCrystalItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL_DUST =
            ITEMS.registerSimpleItem("overload_crystal_dust", new Item.Properties());

    public static final DeferredItem<Item> OVERLOADED_WIRELESS_CONNECTOR = ITEMS.registerItem(
            "overloaded_wireless_connector",
            OverloadedWirelessConnectorItem::new,
            new Item.Properties());

    private ModItems() {
    }
}
