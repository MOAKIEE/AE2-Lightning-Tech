package com.moakiee.ae2lt;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModMenuTypes;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.upgrades.Upgrades;

import com.moakiee.ae2lt.logic.MachineAdapterRegistry;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDecoder;

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
                        output.accept(ModBlocks.SILICON_BLOCK);
                        output.accept(ModItems.OVERLOAD_CRYSTAL);
                        output.accept(ModItems.OVERLOAD_CRYSTAL_DUST);
                        output.accept(ModItems.OVERLOAD_CIRCUIT_BOARD);
                        output.accept(ModItems.OVERLOAD_PROCESSOR);
                        output.accept(ModItems.OVERLOAD_ALLOY);
                        output.accept(ModItems.OVERLOAD_ALLOY_PLATE);
                        output.accept(ModItems.OVERLOAD_SINGULARITY);
                        output.accept(ModItems.ULTIMATE_OVERLOAD_CORE);
                        output.accept(ModBlocks.OVERLOAD_CRYSTAL_BLOCK);
                        output.accept(ModBlocks.OVERLOAD_TNT);
                        output.accept(ModBlocks.HIGH_VOLTAGE_AGGREGATOR);
                        output.accept(ModBlocks.OVERLOADED_CONTROLLER);
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
                        output.accept(ModBlocks.OVERLOADED_PATTERN_PROVIDER);
                        output.accept(ModItems.OVERLOAD_PATTERN);
                        output.accept(ModItems.OVERLOAD_PATTERN_ENCODER);
                        output.accept(ModItems.OVERLOADED_WIRELESS_CONNECTOR);
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

    public AE2LightningTech(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);

        registerOptionalClientIntegrations();
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.HIGH_VOLTAGE_AGGREGATOR.get(),
                (blockEntity, side) -> side == Direction.UP ? null : blockEntity.getEnergyStorage());

        // Expose IN_WORLD_GRID_NODE_HOST so ME cables can connect to our block entity
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOADED_CONTROLLER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlock(
                AECapabilities.GENERIC_INTERNAL_INV,
                (level, pos, state, blockEntity, context) -> {
                    if (blockEntity instanceof OverloadedPatternProviderBlockEntity be) {
                        var logic = (com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic) be.getLogic();
                        return new com.moakiee.ae2lt.logic.InsertOnlyReturnInvWrapper(
                                (com.moakiee.ae2lt.logic.UnlimitedReturnInventory) logic.getInternalReturnInv(),
                                be.getTotalPatternCapacity());
                    }
                    return null;
                },
                ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
    }

    /**
     * After all registries are frozen, bind the AE2 BlockEntityType to the Block.
     * This sets the blockEntityType / class / ticker fields inside AEBaseEntityBlock
     * so that newBlockEntity() and getBlockEntity() work correctly.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var controllerBlock = ModBlocks.OVERLOADED_CONTROLLER.get();
            var controllerBeType = ModBlockEntities.OVERLOADED_CONTROLLER.get();
            controllerBlock.setBlockEntity(
                    OverloadedControllerBlockEntity.class,
                    controllerBeType,
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

            // Register built-in machine adapters (AE2-native fallback)
            MachineAdapterRegistry.init();
            PatternDetailsHelper.registerDecoder(OverloadPatternDecoder.INSTANCE);

            registerAppliedFluxInductionCardCompat();
        });
    }

    private static void registerAppliedFluxInductionCardCompat() {
        var inductionId = ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
        Item inductionCard = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(inductionId);
        if (inductionCard == null || inductionCard == net.minecraft.world.item.Items.AIR) {
            return;
        }

        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get(), 1, "group.pattern_provider.name");
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
