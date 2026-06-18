package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.ExtendedOverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2LightningTech.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningCollectorBlockEntity>>
            LIGHTNING_COLLECTOR = BLOCK_ENTITY_TYPES.register(
                    "lightning_collector",
                    () -> new BlockEntityType<>(
                            LightningCollectorBlockEntity::new,
                            ModBlocks.LIGHTNING_COLLECTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedControllerBlockEntity>>
            OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_controller",
                    () -> new BlockEntityType<>(
                            OverloadedControllerBlockEntity::new,
                            ModBlocks.OVERLOADED_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningSimulationChamberBlockEntity>>
            LIGHTNING_SIMULATION_CHAMBER = BLOCK_ENTITY_TYPES.register(
                    "lightning_simulation_room",
                    () -> new BlockEntityType<>(
                            LightningSimulationChamberBlockEntity::new,
                            ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningAssemblyChamberBlockEntity>>
            LIGHTNING_ASSEMBLY_CHAMBER = BLOCK_ENTITY_TYPES.register(
                    "lightning_assembly_chamber",
                    () -> new BlockEntityType<>(
                            LightningAssemblyChamberBlockEntity::new,
                            ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadProcessingFactoryBlockEntity>>
            OVERLOAD_PROCESSING_FACTORY = BLOCK_ENTITY_TYPES.register(
                    "overload_processing_factory",
                    () -> new BlockEntityType<>(
                            OverloadProcessingFactoryBlockEntity::new,
                            ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeslaCoilBlockEntity>>
            TESLA_COIL = BLOCK_ENTITY_TYPES.register(
                    "tesla_coil",
                    () -> new BlockEntityType<>(
                            TeslaCoilBlockEntity::new,
                            ModBlocks.TESLA_COIL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtmosphericIonizerBlockEntity>>
            ATMOSPHERIC_IONIZER = BLOCK_ENTITY_TYPES.register(
                    "atmospheric_ionizer",
                    () -> new BlockEntityType<>(
                            AtmosphericIonizerBlockEntity::new,
                            ModBlocks.ATMOSPHERIC_IONIZER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalCatalyzerBlockEntity>>
            CRYSTAL_CATALYZER = BLOCK_ENTITY_TYPES.register(
                    "crystal_catalyzer",
                    () -> new BlockEntityType<>(
                            CrystalCatalyzerBlockEntity::new,
                            ModBlocks.CRYSTAL_CATALYZER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedPatternProviderBlockEntity>>
            OVERLOADED_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_pattern_provider",
                    () -> new BlockEntityType<>(
                            OverloadedPatternProviderBlockEntity::new,
                            ModBlocks.OVERLOADED_PATTERN_PROVIDER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtendedOverloadedPatternProviderBlockEntity>>
            EXTENDED_OVERLOADED_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "extended_overloaded_pattern_provider",
                    () -> new BlockEntityType<>(
                            ExtendedOverloadedPatternProviderBlockEntity::new,
                            ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedInterfaceBlockEntity>>
            OVERLOADED_INTERFACE = BLOCK_ENTITY_TYPES.register(
                    "overloaded_interface",
                    () -> new BlockEntityType<>(
                            OverloadedInterfaceBlockEntity::new,
                            ModBlocks.OVERLOADED_INTERFACE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessReceiverBlockEntity>>
            WIRELESS_RECEIVER = BLOCK_ENTITY_TYPES.register(
                    "wireless_receiver",
                    () -> new BlockEntityType<>(
                            WirelessReceiverBlockEntity::new,
                            ModBlocks.WIRELESS_RECEIVER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessOverloadedControllerBlockEntity>>
            WIRELESS_OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "wireless_overloaded_controller",
                    () -> new BlockEntityType<>(
                            WirelessOverloadedControllerBlockEntity::new,
                            ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedWirelessOverloadedControllerBlockEntity>>
            ADVANCED_WIRELESS_OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "advanced_wireless_overloaded_controller",
                    () -> new BlockEntityType<>(
                            AdvancedWirelessOverloadedControllerBlockEntity::new,
                            ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get()));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GhostOutputBlockEntity>>
            GHOST_OUTPUT = BLOCK_ENTITY_TYPES.register(
                    "ghost_output",
                    () -> new BlockEntityType<>(
                            (pos, state) -> new GhostOutputBlockEntity(pos),
                            Blocks.AIR));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FumoBlockEntity>>
            FUMO = BLOCK_ENTITY_TYPES.register(
                    "fumo",
                    () -> new BlockEntityType<>(
                            FumoBlockEntity::new,
                            ModFumos.MOAKIEE_FUMO.get(),
                            ModFumos.CYSTRYSU_FUMO.get(),
                            ModFumos.PIGMEE_FUMO.get()));

    private ModBlockEntities() {
    }
}
