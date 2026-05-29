package com.moakiee.ae2lt;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModAEKeyTypes;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModMenuTypes;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.config.AE2LTConfigMigration;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem.CellOutcome;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEItems;

import com.moakiee.ae2lt.api.frequency.FrequencyApi;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.api.FrequencyApiBridge;
import com.moakiee.ae2lt.me.cell.InfiniteCellHandler;

import com.moakiee.ae2lt.logic.EjectModeRegistry;
import com.moakiee.ae2lt.logic.MachineAdapterRegistry;
import com.moakiee.ae2lt.logic.research.ResearchNoteGenerator;
import com.moakiee.ae2lt.logic.research.ResearchNoteModulationHandler;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDecoder;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

@Mod(AE2LightningTech.MODID)
public class AE2LightningTech {
    public static final String MODID = "ae2lt";

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2lt"))
                    .icon(() -> ModItems.OVERLOAD_CRYSTAL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 方块
                        output.accept(ModBlocks.SILICON_BLOCK.get());
                        output.accept(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get());
                        output.accept(ModBlocks.OVERLOAD_MACHINE_FRAME.get());
                        output.accept(ModBlocks.OVERLOAD_TNT.get());
                        // 机器
                        output.accept(ModBlocks.LIGHTNING_COLLECTOR.get());
                        output.accept(ModBlocks.TESLA_COIL.get());
                        output.accept(ModBlocks.ATMOSPHERIC_IONIZER.get());
                        output.accept(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get());
                        output.accept(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get());
                        output.accept(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get());
                        output.accept(ModBlocks.CRYSTAL_CATALYZER.get());
                        // 网络设备
                        output.accept(ModBlocks.OVERLOADED_CONTROLLER.get());
                        output.accept(ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
                        output.accept(ModBlocks.OVERLOADED_INTERFACE.get());
                        if (ModBlocks.hasOverloadedPowerSupply()) {
                            output.accept(ModBlocks.OVERLOADED_POWER_SUPPLY.get());
                        }
                        output.accept(ModBlocks.WIRELESS_RECEIVER.get());
                        output.accept(ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get());
                        output.accept(ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
                        // 线缆
                        output.accept(ModItems.OVERLOADED_CABLE.get());
                        output.accept(ModItems.OVERLOADED_CABLE_WHITE.get());
                        output.accept(ModItems.OVERLOADED_CABLE_ORANGE.get());
                        output.accept(ModItems.OVERLOADED_CABLE_MAGENTA.get());
                        output.accept(ModItems.OVERLOADED_CABLE_LIGHT_BLUE.get());
                        output.accept(ModItems.OVERLOADED_CABLE_YELLOW.get());
                        output.accept(ModItems.OVERLOADED_CABLE_LIME.get());
                        output.accept(ModItems.OVERLOADED_CABLE_PINK.get());
                        output.accept(ModItems.OVERLOADED_CABLE_GRAY.get());
                        output.accept(ModItems.OVERLOADED_CABLE_LIGHT_GRAY.get());
                        output.accept(ModItems.OVERLOADED_CABLE_CYAN.get());
                        output.accept(ModItems.OVERLOADED_CABLE_PURPLE.get());
                        output.accept(ModItems.OVERLOADED_CABLE_BLUE.get());
                        output.accept(ModItems.OVERLOADED_CABLE_BROWN.get());
                        output.accept(ModItems.OVERLOADED_CABLE_GREEN.get());
                        output.accept(ModItems.OVERLOADED_CABLE_RED.get());
                        output.accept(ModItems.OVERLOADED_CABLE_BLACK.get());
                        // 材料
                        output.accept(ModItems.OVERLOAD_CRYSTAL.get());
                        output.accept(ModItems.OVERLOAD_CRYSTAL_DUST.get());
                        output.accept(ModItems.OVERLOAD_ALLOY.get());
                        output.accept(ModItems.OVERLOAD_ALLOY_BLANK.get());
                        output.accept(ModItems.OVERLOAD_ALLOY_PLATE.get());
                        output.accept(ModItems.OVERLOAD_SINGULARITY.get());
                        output.accept(ModItems.ULTIMATE_OVERLOAD_CORE.get());
                        output.accept(ModItems.LIGHTNING_COLLAPSE_MATRIX.get());
                        output.accept(ModItems.UNOVERLOADED_CIRCUIT_BOARD.get());
                        output.accept(ModItems.OVERLOAD_CIRCUIT_BOARD.get());
                        output.accept(ModItems.OVERLOAD_PROCESSOR.get());
                        output.accept(ModItems.OVERLOAD_INSCRIBER_PRESS.get());
                        output.accept(ModItems.ELECTRO_CHIME_CRYSTAL.get());
                        output.accept(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get());
                        output.accept(ModItems.CLEAR_CONDENSATE.get());
                        output.accept(ModItems.RAIN_CONDENSATE.get());
                        output.accept(ModItems.THUNDERSTORM_CONDENSATE.get());
                        // 存储组件
                        output.accept(ModItems.LIGHTNING_ITEM_CELL_HOUSING.get());
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_I.get());
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_II.get());
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_III.get());
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_IV.get());
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_V.get());
                        // 元件
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_I.get());
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_II.get());
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_III.get());
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_IV.get());
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_V.get());
                        // 无限存储单元
                        output.accept(ModItems.INFINITE_STORAGE_CELL.get());
                        output.accept(FixedInfiniteCellItem.createDisplayedResultStack(CellOutcome.HIGH_VOLTAGE));
                        output.accept(FixedInfiniteCellItem.createDisplayedResultStack(CellOutcome.EXTREME_HIGH_VOLTAGE));
                        // 工具
                        output.accept(ModItems.OVERLOAD_PATTERN.get());
                        output.accept(ModItems.OVERLOAD_PATTERN_ENCODER.get());
                        output.accept(ModItems.OVERLOADED_WIRELESS_CONNECT_TOOL.get());
                        output.accept(ModItems.OVERLOADED_FILTER_COMPONENT.get());
                        // 水晶生长
                        output.accept(ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL.get());
                        output.accept(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get());
                        output.accept(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get());
                        output.accept(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get());
                        output.accept(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get());
                        output.accept(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get());
                        output.accept(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get());
                        output.accept(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get());
                        // Fumo
                        output.accept(ModFumos.MOAKIEE_FUMO_ITEM.get());
                        output.accept(ModFumos.CYSTRYSU_FUMO_ITEM.get());
                        output.accept(ModFumos.PIGMEE_FUMO_ITEM.get());
                    })
                    .build());

    public AE2LightningTech() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AE2LTCommonConfig.SPEC);
        AE2LTConfigMigration.runIfNeeded();
        ModFumos.register();
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModAEKeyTypes::register);
        modEventBus.addListener(this::commonSetup);

        com.moakiee.ae2lt.network.NetworkInit.register();

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTickPost);
        MinecraftForge.EVENT_BUS.register(new ResearchNoteModulationHandler());
    }

    /**
     * After all registries are frozen, bind the AE2 BlockEntityType to the Block.
     * This sets the blockEntityType / class / ticker fields inside AEBaseEntityBlock
     * so that newBlockEntity() and getBlockEntity() work correctly.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        FrequencyApi.setProvider(new FrequencyApiBridge());
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
                    LightningSimulationChamberBlockEntity::serverTick);

            var assemblyBlock = ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get();
            var assemblyBeType = ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get();
            assemblyBlock.setBlockEntity(
                    LightningAssemblyChamberBlockEntity.class,
                    assemblyBeType,
                    null,
                    LightningAssemblyChamberBlockEntity::serverTick);

            var overloadProcessingFactoryBlock = ModBlocks.OVERLOAD_PROCESSING_FACTORY.get();
            var overloadProcessingFactoryBeType = ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get();
            overloadProcessingFactoryBlock.setBlockEntity(
                    OverloadProcessingFactoryBlockEntity.class,
                    overloadProcessingFactoryBeType,
                    null,
                    OverloadProcessingFactoryBlockEntity::serverTick);

            var teslaCoilBlock = ModBlocks.TESLA_COIL.get();
            var teslaCoilBeType = ModBlockEntities.TESLA_COIL.get();
            teslaCoilBlock.setBlockEntity(
                    TeslaCoilBlockEntity.class,
                    teslaCoilBeType,
                    null,
                    TeslaCoilBlockEntity::serverTick);

            var atmosphericIonizerBlock = ModBlocks.ATMOSPHERIC_IONIZER.get();
            var atmosphericIonizerBeType = ModBlockEntities.ATMOSPHERIC_IONIZER.get();
            atmosphericIonizerBlock.setBlockEntity(
                    AtmosphericIonizerBlockEntity.class,
                    atmosphericIonizerBeType,
                    null,
                    AtmosphericIonizerBlockEntity::serverTick);

            var crystalCatalyzerBlock = ModBlocks.CRYSTAL_CATALYZER.get();
            var crystalCatalyzerBeType = ModBlockEntities.CRYSTAL_CATALYZER.get();
            crystalCatalyzerBlock.setBlockEntity(
                    CrystalCatalyzerBlockEntity.class,
                    crystalCatalyzerBeType,
                    null,
                    CrystalCatalyzerBlockEntity::serverTick);

            var block = ModBlocks.OVERLOADED_PATTERN_PROVIDER.get();
            var beType = ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get();
            block.setBlockEntity(
                    OverloadedPatternProviderBlockEntity.class,
                    beType,
                    null,
                    OverloadedPatternProviderBlockEntity::serverTick
            );

            var interfaceBlock = ModBlocks.OVERLOADED_INTERFACE.get();
            var interfaceBeType = ModBlockEntities.OVERLOADED_INTERFACE.get();
            interfaceBlock.setBlockEntity(
                    OverloadedInterfaceBlockEntity.class,
                    interfaceBeType,
                    null,
                    OverloadedInterfaceBlockEntity::serverTick);

            if (ModBlocks.hasOverloadedPowerSupply()) {
                var powerSupplyBlock = ModBlocks.OVERLOADED_POWER_SUPPLY.get();
                var powerSupplyBeType = ModBlockEntities.OVERLOADED_POWER_SUPPLY.get();
                powerSupplyBlock.setBlockEntity(
                        OverloadedPowerSupplyBlockEntity.class,
                        powerSupplyBeType,
                        null,
                        OverloadedPowerSupplyBlockEntity::serverTick);
            }

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
            if (ModBlocks.hasOverloadedPowerSupply()) {
                appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                        ModBlockEntities.OVERLOADED_POWER_SUPPLY.get(),
                        ModBlocks.OVERLOADED_POWER_SUPPLY.get().asItem());
            }
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
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    crystalCatalyzerBeType,
                    crystalCatalyzerBlock.asItem());

            setupWirelessControllerBlock(
                    ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get(),
                    ModBlockEntities.WIRELESS_OVERLOADED_CONTROLLER.get(),
                    WirelessOverloadedControllerBlockEntity.class,
                    (level, pos, state, be) -> WirelessOverloadedControllerBlockEntity.wirelessServerTick(
                            level, pos, state, (WirelessOverloadedControllerBlockEntity) be));

            setupWirelessControllerBlock(
                    ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                    ModBlockEntities.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                    AdvancedWirelessOverloadedControllerBlockEntity.class,
                    (level, pos, state, be) ->
                            AdvancedWirelessOverloadedControllerBlockEntity.advancedWirelessServerTick(
                                    level,
                                    pos,
                                    state,
                                    (AdvancedWirelessOverloadedControllerBlockEntity) be));

            var wirelessReceiverBlock = ModBlocks.WIRELESS_RECEIVER.get();
            var wirelessReceiverBeType = ModBlockEntities.WIRELESS_RECEIVER.get();
            wirelessReceiverBlock.setBlockEntity(
                    WirelessReceiverBlockEntity.class,
                    wirelessReceiverBeType,
                    null,
                    (level, pos, state, be) -> ((WirelessReceiverBlockEntity) be).serverTick());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    wirelessReceiverBeType,
                    wirelessReceiverBlock.asItem());

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
            Upgrades.add(AEItems.INVERTER_CARD, ModItems.OVERLOADED_FILTER_COMPONENT.get(), 1);
            Upgrades.add(AEItems.CRAFTING_CARD, ModBlocks.OVERLOADED_INTERFACE.get(), 1);
            Upgrades.add(AEItems.FUZZY_CARD, ModBlocks.OVERLOADED_INTERFACE.get(), 1);

            registerAppliedFluxInductionCardCompat();
            registerOverloadTntDispenseBehavior();

        });
    }

    private static void registerOverloadTntDispenseBehavior() {
        net.minecraft.world.level.block.DispenserBlock.registerBehavior(
                ModBlocks.OVERLOAD_TNT.get().asItem(),
                new net.minecraft.core.dispenser.DefaultDispenseItemBehavior() {
                    @Override
                    protected net.minecraft.world.item.ItemStack execute(
                            net.minecraft.core.BlockSource source,
                            net.minecraft.world.item.ItemStack stack) {
                        var level = source.getLevel();
                        var pos = source.getPos().relative(
                                source.getBlockState().getValue(
                                        net.minecraft.world.level.block.DispenserBlock.FACING));
                        var tnt = new com.moakiee.ae2lt.entity.OverloadTntEntity(
                                level,
                                pos.getX() + 0.5D,
                                pos.getY(),
                                pos.getZ() + 0.5D,
                                null);
                        level.addFreshEntity(tnt);
                        level.playSound(
                                null,
                                tnt.getX(),
                                tnt.getY(),
                                tnt.getZ(),
                                net.minecraft.sounds.SoundEvents.TNT_PRIMED,
                                net.minecraft.sounds.SoundSource.BLOCKS,
                                1.0F,
                                1.0F);
                        level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, pos);
                        stack.shrink(1);
                        return stack;
                    }
                });
    }

    private static void registerAppliedFluxInductionCardCompat() {
        var inductionId = new ResourceLocation("appflux", "induction_card");
        Item inductionCard = ForgeRegistries.ITEMS.getValue(inductionId);
        if (inductionCard == null || inductionCard == net.minecraft.world.item.Items.AIR) {
            return;
        }

        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get(), 1, "group.pattern_provider.name");
        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_INTERFACE.get(), 1);
    }

    private void onServerStarting(ServerStartingEvent event) {
        EjectModeRegistry.onServerStart(event.getServer());
        WirelessFrequencyManager.onServerStart(event.getServer());
        ResearchNoteGenerator.onServerStarting();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        EjectModeRegistry.onServerStop();
        WirelessFrequencyManager.onServerStop();
        ResearchNoteGenerator.onServerStopped();
    }

    private void onServerTickPost(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        WirelessFrequencyManager.flushPendingDeviceNotifications();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setupWirelessControllerBlock(
            AEBaseEntityBlock block,
            BlockEntityType beType,
            Class beClass,
            net.minecraft.world.level.block.entity.BlockEntityTicker serverTicker) {
        block.setBlockEntity(beClass, beType, null, serverTicker);
        AEBaseBlockEntity.registerBlockEntityItem(beType, block.asItem());
    }
}
