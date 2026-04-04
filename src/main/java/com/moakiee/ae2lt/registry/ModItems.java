package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.InfiniteStorageCellItem;
import com.moakiee.ae2lt.item.LightningStorageComponentItem;
import com.moakiee.ae2lt.item.OverloadCrystalItem;
import com.moakiee.ae2lt.item.OverloadPatternEncoderItem;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.item.PerfectElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.WeatherCondensateItem;
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

    public static final DeferredItem<ElectroChimeCrystalItem> ELECTRO_CHIME_CRYSTAL = ITEMS.registerItem(
            "electro_chime_crystal",
            ElectroChimeCrystalItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<PerfectElectroChimeCrystalItem> PERFECT_ELECTRO_CHIME_CRYSTAL = ITEMS.registerItem(
            "perfect_electro_chime_crystal",
            PerfectElectroChimeCrystalItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<WeatherCondensateItem> CLEAR_CONDENSATE = ITEMS.register(
            "clear_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.CLEAR, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<WeatherCondensateItem> RAIN_CONDENSATE = ITEMS.register(
            "rain_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.RAIN, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<WeatherCondensateItem> THUNDERSTORM_CONDENSATE = ITEMS.register(
            "thunderstorm_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.THUNDERSTORM, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_I =
            registerLightningStorageComponent("lightning_storage_component_i", 64, 32);
    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_II =
            registerLightningStorageComponent("lightning_storage_component_ii", 256, 128);
    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_III =
            registerLightningStorageComponent("lightning_storage_component_iii", 1024, 512);
    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_IV =
            registerLightningStorageComponent("lightning_storage_component_iv", 4096, 2048);
    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_V =
            registerLightningStorageComponent("lightning_storage_component_v", 16384, 8192);

    public static final DeferredItem<InfiniteStorageCellItem> INFINITE_STORAGE_CELL =
            ITEMS.register("infinite_storage_cell",
                    () -> new InfiniteStorageCellItem(
                            new Item.Properties(),
                            Long.MAX_VALUE, Long.MAX_VALUE,
                            8, Integer.MAX_VALUE,
                            32));

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
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_I, "1k_item_cell");
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_II, "4k_item_cell");
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_III, "16k_item_cell");
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_IV, "64k_item_cell");
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_V, "256k_item_cell");
        registerStorageCellModel(INFINITE_STORAGE_CELL, "256k_item_cell");
    }

    private static DeferredItem<LightningStorageComponentItem> registerLightningStorageComponent(
            String id,
            int totalBytes,
            double idleDrain) {
        return ITEMS.register(id, () -> new LightningStorageComponentItem(totalBytes, idleDrain));
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
