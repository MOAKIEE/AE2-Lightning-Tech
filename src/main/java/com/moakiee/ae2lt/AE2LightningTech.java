package com.moakiee.ae2lt;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModAEKeyTypes;
import com.moakiee.ae2lt.registry.ModMenuTypes;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEItems;

import net.minecraft.world.level.block.entity.BlockEntityType;

import com.moakiee.ae2lt.me.cell.InfiniteCellHandler;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.EjectModeRegistry;
import com.moakiee.ae2lt.logic.MachineAdapterRegistry;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDecoder;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(AE2LightningTech.MODID)
public class AE2LightningTech {
    public static final String MODID = "ae2lt";

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2lt"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.OVERLOAD_CRYSTAL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 方块
                        output.accept(ModBlocks.SILICON_BLOCK);
                        output.accept(ModBlocks.OVERLOAD_CRYSTAL_BLOCK);
                        output.accept(ModBlocks.OVERLOAD_MACHINE_FRAME);
                        output.accept(ModBlocks.OVERLOAD_TNT);
                        // 机器
                        output.accept(ModBlocks.LIGHTNING_COLLECTOR);
                        output.accept(ModBlocks.TESLA_COIL);
                        output.accept(ModBlocks.ATMOSPHERIC_IONIZER);
                        output.accept(ModBlocks.LIGHTNING_SIMULATION_CHAMBER);
                        output.accept(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER);
                        output.accept(ModBlocks.OVERLOAD_PROCESSING_FACTORY);
                        // 网络设备
                        output.accept(ModBlocks.OVERLOADED_CONTROLLER);
                        output.accept(ModBlocks.OVERLOADED_PATTERN_PROVIDER);
                        output.accept(ModBlocks.OVERLOADED_INTERFACE);
                        // 线缆
                        output.accept(ModItems.OVERLOADED_CABLE);
                        output.accept(ModItems.OVERLOADED_CABLE_WHITE);
                        output.accept(ModItems.OVERLOADED_CABLE_ORANGE);
                        output.accept(ModItems.OVERLOADED_CABLE_MAGENTA);
                        output.accept(ModItems.OVERLOADED_CABLE_LIGHT_BLUE);
                        output.accept(ModItems.OVERLOADED_CABLE_YELLOW);
                        output.accept(ModItems.OVERLOADED_CABLE_LIME);
                        output.accept(ModItems.OVERLOADED_CABLE_PINK);
                        output.accept(ModItems.OVERLOADED_CABLE_GRAY);
                        output.accept(ModItems.OVERLOADED_CABLE_LIGHT_GRAY);
                        output.accept(ModItems.OVERLOADED_CABLE_CYAN);
                        output.accept(ModItems.OVERLOADED_CABLE_PURPLE);
                        output.accept(ModItems.OVERLOADED_CABLE_BLUE);
                        output.accept(ModItems.OVERLOADED_CABLE_BROWN);
                        output.accept(ModItems.OVERLOADED_CABLE_GREEN);
                        output.accept(ModItems.OVERLOADED_CABLE_RED);
                        output.accept(ModItems.OVERLOADED_CABLE_BLACK);
                        // 材料
                        output.accept(ModItems.OVERLOAD_CRYSTAL);
                        output.accept(ModItems.OVERLOAD_CRYSTAL_DUST);
                        output.accept(ModItems.OVERLOAD_ALLOY);
                        output.accept(ModItems.OVERLOAD_ALLOY_BLANK);
                        output.accept(ModItems.OVERLOAD_ALLOY_PLATE);
                        output.accept(ModItems.OVERLOAD_SINGULARITY);
                        output.accept(ModItems.ULTIMATE_OVERLOAD_CORE);
                        output.accept(ModItems.LIGHTNING_COLLAPSE_MATRIX);
                        output.accept(ModItems.UNOVERLOADED_CIRCUIT_BOARD);
                        output.accept(ModItems.OVERLOAD_CIRCUIT_BOARD);
                        output.accept(ModItems.OVERLOAD_PROCESSOR);
                        output.accept(ModItems.OVERLOAD_INSCRIBER_PRESS);
                        output.accept(ModItems.ELECTRO_CHIME_CRYSTAL);
                        output.accept(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL);
                        output.accept(ModItems.CLEAR_CONDENSATE);
                        output.accept(ModItems.RAIN_CONDENSATE);
                        output.accept(ModItems.THUNDERSTORM_CONDENSATE);
                        // 存储组件
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_I);
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_II);
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_III);
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_IV);
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_V);
                        // 元件
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_I);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_II);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_III);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_IV);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_V);
                        // 无限存储单元
                        output.accept(ModItems.INFINITE_STORAGE_CELL);
                        output.accept(ModItems.MYSTERIOUS_CELL);
                        // 工具
                        output.accept(ModItems.OVERLOAD_PATTERN);
                        output.accept(ModItems.OVERLOAD_PATTERN_ENCODER);
                        output.accept(ModItems.OVERLOADED_WIRELESS_CONNECT_TOOL);
                        output.accept(ModItems.OVERLOADED_FILTER_COMPONENT);
                        // 无线设备
                        output.accept(ModBlocks.WIRELESS_RECEIVER);
                        output.accept(ModBlocks.WIRELESS_OVERLOADED_CONTROLLER);
                        output.accept(ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER);
                        // 水晶生长
                        output.accept(ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL);
                        output.accept(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL);
                        output.accept(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL);
                        output.accept(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL);
                        output.accept(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD);
                        output.accept(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD);
                        output.accept(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD);
                        output.accept(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER);
                    })
                    .build());

    public AE2LightningTech(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModAEKeyTypes::register);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, AE2LTCommonConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);

        registerOptionalClientIntegrations();
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LIGHTNING_COLLECTOR.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.TESLA_COIL.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ATMOSPHERIC_IONIZER.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, side) -> blockEntity.getFluidHandlerCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.TESLA_COIL.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        // Expose IN_WORLD_GRID_NODE_HOST so ME cables can connect to our block entity
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LIGHTNING_COLLECTOR.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOADED_CONTROLLER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.TESLA_COIL.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ATMOSPHERIC_IONIZER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOADED_INTERFACE.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.WIRELESS_OVERLOADED_CONTROLLER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.WIRELESS_RECEIVER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlock(
                AECapabilities.GENERIC_INTERNAL_INV,
                (level, pos, state, blockEntity, context) -> {
                    if (blockEntity instanceof OverloadedPatternProviderBlockEntity be) {
                        var logic = (com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic) be.getLogic();
                        return new com.moakiee.ae2lt.logic.InsertOnlyReturnInvWrapper(
                                (com.moakiee.ae2lt.logic.UnlimitedReturnInventory) logic.getInternalReturnInv());
                    }
                    return null;
                },
                ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());

        event.registerBlock(
                AECapabilities.GENERIC_INTERNAL_INV,
                (level, pos, state, blockEntity, context) -> {
                    if (blockEntity instanceof OverloadedInterfaceBlockEntity be) {
                        var logic = be.getInterfaceLogic();
                        if (logic instanceof com.moakiee.ae2lt.logic.OverloadedInterfaceLogic ol) {
                            return ol.getProxiedStorage();
                        }
                    }
                    return null;
                },
                ModBlocks.OVERLOADED_INTERFACE.get());
    }

    /**
     * After all registries are frozen, bind the AE2 BlockEntityType to the Block.
     * This sets the blockEntityType / class / ticker fields inside AEBaseEntityBlock
     * so that newBlockEntity() and getBlockEntity() work correctly.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var lightningCollectorBlock = ModBlocks.LIGHTNING_COLLECTOR.get();
            var lightningCollectorBeType = ModBlockEntities.LIGHTNING_COLLECTOR.get();
            lightningCollectorBlock.setBlockEntity(
                    LightningCollectorBlockEntity.class,
                    lightningCollectorBeType,
                    null,
                    LightningCollectorBlockEntity::serverTick);

            var controllerBlock = ModBlocks.OVERLOADED_CONTROLLER.get();
            var controllerBeType = ModBlockEntities.OVERLOADED_CONTROLLER.get();
            controllerBlock.setBlockEntity(
                    OverloadedControllerBlockEntity.class,
                    controllerBeType,
                    null,
                    OverloadedControllerBlockEntity::serverTick);

            var lightningChamberBlock = ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get();
            var lightningChamberBeType = ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get();
            lightningChamberBlock.setBlockEntity(
                    LightningSimulationChamberBlockEntity.class,
                    lightningChamberBeType,
                    null,
                    null);

            var assemblyBlock = ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get();
            var assemblyBeType = ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get();
            assemblyBlock.setBlockEntity(
                    LightningAssemblyChamberBlockEntity.class,
                    assemblyBeType,
                    null,
                    null);

            var overloadProcessingFactoryBlock = ModBlocks.OVERLOAD_PROCESSING_FACTORY.get();
            var overloadProcessingFactoryBeType = ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get();
            overloadProcessingFactoryBlock.setBlockEntity(
                    OverloadProcessingFactoryBlockEntity.class,
                    overloadProcessingFactoryBeType,
                    null,
                    null);

            var teslaCoilBlock = ModBlocks.TESLA_COIL.get();
            var teslaCoilBeType = ModBlockEntities.TESLA_COIL.get();
            teslaCoilBlock.setBlockEntity(
                    TeslaCoilBlockEntity.class,
                    teslaCoilBeType,
                    null,
                    null);

            var atmosphericIonizerBlock = ModBlocks.ATMOSPHERIC_IONIZER.get();
            var atmosphericIonizerBeType = ModBlockEntities.ATMOSPHERIC_IONIZER.get();
            atmosphericIonizerBlock.setBlockEntity(
                    AtmosphericIonizerBlockEntity.class,
                    atmosphericIonizerBeType,
                    null,
                    null);

            var block = ModBlocks.OVERLOADED_PATTERN_PROVIDER.get();
            var beType = ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get();
            block.setBlockEntity(
                    OverloadedPatternProviderBlockEntity.class,
                    beType,
                    null,
                    null
            );

            var interfaceBlock = ModBlocks.OVERLOADED_INTERFACE.get();
            var interfaceBeType = ModBlockEntities.OVERLOADED_INTERFACE.get();
            interfaceBlock.setBlockEntity(
                    OverloadedInterfaceBlockEntity.class,
                    interfaceBeType,
                    null,
                    OverloadedInterfaceBlockEntity::serverTick);

            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    lightningCollectorBeType,
                    lightningCollectorBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.OVERLOADED_CONTROLLER.get(),
                    ModBlocks.OVERLOADED_CONTROLLER.get().asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get(),
                    ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    interfaceBeType,
                    interfaceBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                    ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get().asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    assemblyBeType,
                    assemblyBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    overloadProcessingFactoryBeType,
                    overloadProcessingFactoryBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    teslaCoilBeType,
                    teslaCoilBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    atmosphericIonizerBeType,
                    atmosphericIonizerBlock.asItem());

            MachineAdapterRegistry.init();
            PatternDetailsHelper.registerDecoder(OverloadPatternDecoder.INSTANCE);
            StorageCells.addCellHandler(InfiniteCellHandler.INSTANCE);
            ModItems.registerStorageCellModels();
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get(),
                    LightningSimulationChamberBlockEntity.SPEED_CARD_SLOTS);
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                    LightningAssemblyChamberBlockEntity.SPEED_CARD_SLOTS);
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get(),
                    OverloadProcessingFactoryBlockEntity.SPEED_CARD_SLOTS);

            Upgrades.add(AEItems.FUZZY_CARD, ModItems.OVERLOADED_FILTER_COMPONENT.get(), 1);
            Upgrades.add(AEItems.CRAFTING_CARD, ModBlocks.OVERLOADED_INTERFACE.get(), 1);
            Upgrades.add(AEItems.FUZZY_CARD, ModBlocks.OVERLOADED_INTERFACE.get(), 1);

            registerAppliedFluxInductionCardCompat();

            setupWirelessControllerBlock(
                    ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get(),
                    ModBlockEntities.WIRELESS_OVERLOADED_CONTROLLER.get(),
                    WirelessOverloadedControllerBlockEntity.class,
                    (net.minecraft.world.level.block.entity.BlockEntityTicker) (l, p, s, be) ->
                            WirelessOverloadedControllerBlockEntity.wirelessServerTick(
                                    l, p, s, (WirelessOverloadedControllerBlockEntity) be));

            setupWirelessControllerBlock(
                    ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                    ModBlockEntities.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                    AdvancedWirelessOverloadedControllerBlockEntity.class,
                    (net.minecraft.world.level.block.entity.BlockEntityTicker) (l, p, s, be) ->
                            AdvancedWirelessOverloadedControllerBlockEntity.advancedWirelessServerTick(
                                    l, p, s, (AdvancedWirelessOverloadedControllerBlockEntity) be));

            var wirelessReceiverBlock = ModBlocks.WIRELESS_RECEIVER.get();
            var wirelessReceiverBeType = ModBlockEntities.WIRELESS_RECEIVER.get();
            wirelessReceiverBlock.setBlockEntity(
                    WirelessReceiverBlockEntity.class,
                    wirelessReceiverBeType,
                    null,
                    (net.minecraft.world.level.block.entity.BlockEntityTicker)
                            (l, p, s, be) -> ((WirelessReceiverBlockEntity) be).serverTick());

            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    wirelessReceiverBeType,
                    wirelessReceiverBlock.asItem());

        });
    }

    private static void registerAppliedFluxInductionCardCompat() {
        var inductionId = ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
        Item inductionCard = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(inductionId);
        if (inductionCard == null || inductionCard == net.minecraft.world.item.Items.AIR) {
            return;
        }

        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get(), 1, "group.pattern_provider.name");
        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_INTERFACE.get(), 1);
    }

    private void onServerStarting(ServerStartingEvent event) {
        EjectModeRegistry.onServerStart(event.getServer());
        WirelessFrequencyManager.onServerStart(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        EjectModeRegistry.onServerStop();
        WirelessFrequencyManager.onServerStop();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setupWirelessControllerBlock(
            AEBaseEntityBlock block,
            BlockEntityType beType,
            Class beClass,
            net.minecraft.world.level.block.entity.BlockEntityTicker serverTicker) {
        block.setBlockEntity(beClass, beType, null, serverTicker);
        AEBaseBlockEntity.registerBlockEntityItem(beType, block.asItem());
    }

    private static void registerOptionalClientIntegrations() {
        if (!FMLEnvironment.dist.isClient() || !ModList.get().isLoaded("ponder")) {
            return;
        }

        try {
            Class.forName("com.moakiee.ae2lt.integration.ponder.PonderCompat")
                    .getMethod("register")
                    .invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
