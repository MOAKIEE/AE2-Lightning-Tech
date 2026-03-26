package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.LightningStorageCellItem;
import com.moakiee.ae2lt.item.OverloadCrystalItem;
import com.moakiee.ae2lt.item.OverloadPatternEncoderItem;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.part.OverloadedCablePart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.client.StorageCellModels;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2LightningTech.MODID);

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL = ITEMS.registerItem(
            "overload_crystal",
            OverloadCrystalItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL_DUST =
            ITEMS.registerSimpleItem("overload_crystal_dust", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("overload_circuit_board", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_PROCESSOR =
            ITEMS.registerSimpleItem("overload_processor", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_INSCRIBER_PRESS =
            ITEMS.registerSimpleItem("overload_inscriber_press", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_ALLOY =
            ITEMS.registerSimpleItem("overload_alloy", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_ALLOY_PLATE =
            ITEMS.registerSimpleItem("overload_alloy_plate", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_SINGULARITY =
            ITEMS.registerSimpleItem("overload_singularity", new Item.Properties());

    public static final DeferredItem<Item> ULTIMATE_OVERLOAD_CORE =
            ITEMS.registerSimpleItem("ultimate_overload_core", new Item.Properties());

    public static final DeferredItem<Item> LIGHTNING_COLLAPSE_MATRIX =
            ITEMS.registerSimpleItem("lightning_collapse_matrix", new Item.Properties());

    public static final DeferredItem<Item> LIGHTNING_STORAGE_COMPONENT_1K =
            ITEMS.registerSimpleItem("lightning_storage_component_1k", new Item.Properties());
    public static final DeferredItem<Item> LIGHTNING_STORAGE_COMPONENT_4K =
            ITEMS.registerSimpleItem("lightning_storage_component_4k", new Item.Properties());
    public static final DeferredItem<Item> LIGHTNING_STORAGE_COMPONENT_16K =
            ITEMS.registerSimpleItem("lightning_storage_component_16k", new Item.Properties());
    public static final DeferredItem<Item> LIGHTNING_STORAGE_COMPONENT_64K =
            ITEMS.registerSimpleItem("lightning_storage_component_64k", new Item.Properties());
    public static final DeferredItem<Item> LIGHTNING_STORAGE_COMPONENT_256K =
            ITEMS.registerSimpleItem("lightning_storage_component_256k", new Item.Properties());

    public static final DeferredItem<LightningStorageCellItem> LIGHTNING_STORAGE_CELL_1K =
            registerLightningStorageCell("lightning_storage_cell_1k", 1);
    public static final DeferredItem<LightningStorageCellItem> LIGHTNING_STORAGE_CELL_4K =
            registerLightningStorageCell("lightning_storage_cell_4k", 4);
    public static final DeferredItem<LightningStorageCellItem> LIGHTNING_STORAGE_CELL_16K =
            registerLightningStorageCell("lightning_storage_cell_16k", 16);
    public static final DeferredItem<LightningStorageCellItem> LIGHTNING_STORAGE_CELL_64K =
            registerLightningStorageCell("lightning_storage_cell_64k", 64);
    public static final DeferredItem<LightningStorageCellItem> LIGHTNING_STORAGE_CELL_256K =
            registerLightningStorageCell("lightning_storage_cell_256k", 256);

    public static final DeferredItem<Item> OVERLOADED_WIRELESS_CONNECT_TOOL = ITEMS.registerItem(
            "overloaded_wireless_connect_tool",
            OverloadedWirelessConnectorItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_PATTERN = ITEMS.registerItem(
            "overload_pattern",
            OverloadPatternItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_PATTERN_ENCODER = ITEMS.registerItem(
            "overload_pattern_encoder",
            OverloadPatternEncoderItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOADED_FILTER_COMPONENT = ITEMS.registerItem(
            "overloaded_filter_component",
            OverloadedFilterComponentItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE =
            registerOverloadedCable("overloaded_cable", AEColor.TRANSPARENT);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_WHITE =
            registerOverloadedCable("overloaded_cable_white", AEColor.WHITE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_ORANGE =
            registerOverloadedCable("overloaded_cable_orange", AEColor.ORANGE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_MAGENTA =
            registerOverloadedCable("overloaded_cable_magenta", AEColor.MAGENTA);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIGHT_BLUE =
            registerOverloadedCable("overloaded_cable_light_blue", AEColor.LIGHT_BLUE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_YELLOW =
            registerOverloadedCable("overloaded_cable_yellow", AEColor.YELLOW);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIME =
            registerOverloadedCable("overloaded_cable_lime", AEColor.LIME);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_PINK =
            registerOverloadedCable("overloaded_cable_pink", AEColor.PINK);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_GRAY =
            registerOverloadedCable("overloaded_cable_gray", AEColor.GRAY);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIGHT_GRAY =
            registerOverloadedCable("overloaded_cable_light_gray", AEColor.LIGHT_GRAY);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_CYAN =
            registerOverloadedCable("overloaded_cable_cyan", AEColor.CYAN);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_PURPLE =
            registerOverloadedCable("overloaded_cable_purple", AEColor.PURPLE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BLUE =
            registerOverloadedCable("overloaded_cable_blue", AEColor.BLUE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BROWN =
            registerOverloadedCable("overloaded_cable_brown", AEColor.BROWN);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_GREEN =
            registerOverloadedCable("overloaded_cable_green", AEColor.GREEN);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_RED =
            registerOverloadedCable("overloaded_cable_red", AEColor.RED);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BLACK =
            registerOverloadedCable("overloaded_cable_black", AEColor.BLACK);

    private ModItems() {
    }

    public static void registerStorageCellModels() {
        registerStorageCellModel(LIGHTNING_STORAGE_CELL_1K, "1k_item_cell");
        registerStorageCellModel(LIGHTNING_STORAGE_CELL_4K, "4k_item_cell");
        registerStorageCellModel(LIGHTNING_STORAGE_CELL_16K, "16k_item_cell");
        registerStorageCellModel(LIGHTNING_STORAGE_CELL_64K, "64k_item_cell");
        registerStorageCellModel(LIGHTNING_STORAGE_CELL_256K, "256k_item_cell");
    }

    private static DeferredItem<LightningStorageCellItem> registerLightningStorageCell(String id, int kilobytes) {
        return ITEMS.register(id, () -> new LightningStorageCellItem(kilobytes));
    }

    private static void registerStorageCellModel(DeferredItem<? extends Item> item, String modelName) {
        StorageCellModels.registerModel(
                item.get(),
                ResourceLocation.fromNamespaceAndPath("ae2", "block/drive/cells/" + modelName));
    }

    private static DeferredItem<ColoredPartItem<OverloadedCablePart>> registerOverloadedCable(String id, AEColor color) {
        return ITEMS.register(
                id,
                () -> new ColoredPartItem<>(
                        new Item.Properties(),
                        OverloadedCablePart.class,
                        OverloadedCablePart::new,
                        color));
    }
}
