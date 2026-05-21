package com.moakiee.ae2lt.client;

import appeng.api.client.StorageCellModels;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class StorageCellModelClientInit {
    private StorageCellModelClientInit() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(StorageCellModelClientInit::registerStorageCellModels);
    }

    private static void registerStorageCellModels() {
        registerStorageCellModel(ModItems.LIGHTNING_STORAGE_COMPONENT_I);
        registerStorageCellModel(ModItems.LIGHTNING_STORAGE_COMPONENT_II);
        registerStorageCellModel(ModItems.LIGHTNING_STORAGE_COMPONENT_III);
        registerStorageCellModel(ModItems.LIGHTNING_STORAGE_COMPONENT_IV);
        registerStorageCellModel(ModItems.LIGHTNING_STORAGE_COMPONENT_V);
        registerStorageCellModel(ModItems.INFINITE_STORAGE_CELL);
        registerStorageCellModel(ModItems.MYSTERIOUS_CELL, "256k_item_cell");
    }

    private static void registerStorageCellModel(DeferredItem<? extends Item> item) {
        StorageCellModels.registerModel(
                item.get(),
                Identifier.fromNamespaceAndPath(
                        AE2LightningTech.MODID,
                        "block/drive/cells/" + item.getId().getPath()));
    }

    private static void registerStorageCellModel(DeferredItem<? extends Item> item, String modelName) {
        StorageCellModels.registerModel(
                item.get(),
                Identifier.fromNamespaceAndPath("ae2", "block/drive_" + modelName));
    }
}
