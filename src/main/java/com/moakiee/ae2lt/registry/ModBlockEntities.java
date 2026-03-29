package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
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
                    () -> BlockEntityType.Builder.of(
                            LightningCollectorBlockEntity::new,
                            ModBlocks.LIGHTNING_COLLECTOR.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedControllerBlockEntity>>
            OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_controller",
                    () -> BlockEntityType.Builder.of(
                            OverloadedControllerBlockEntity::new,
                            ModBlocks.OVERLOADED_CONTROLLER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningSimulationChamberBlockEntity>>
            LIGHTNING_SIMULATION_CHAMBER = BLOCK_ENTITY_TYPES.register(
                    "lightning_simulation_room",
                    () -> BlockEntityType.Builder.of(
                            LightningSimulationChamberBlockEntity::new,
                            ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeslaCoilBlockEntity>>
            TESLA_COIL = BLOCK_ENTITY_TYPES.register(
                    "tesla_coil",
                    () -> BlockEntityType.Builder.of(
                            TeslaCoilBlockEntity::new,
                            ModBlocks.TESLA_COIL.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedPatternProviderBlockEntity>>
            OVERLOADED_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_pattern_provider",
                    () -> BlockEntityType.Builder.of(
                            OverloadedPatternProviderBlockEntity::new,
                            ModBlocks.OVERLOADED_PATTERN_PROVIDER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedInterfaceBlockEntity>>
            OVERLOADED_INTERFACE = BLOCK_ENTITY_TYPES.register(
                    "overloaded_interface",
                    () -> BlockEntityType.Builder.of(
                            OverloadedInterfaceBlockEntity::new,
                            ModBlocks.OVERLOADED_INTERFACE.get())
                            .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GhostOutputBlockEntity>>
            GHOST_OUTPUT = BLOCK_ENTITY_TYPES.register(
                    "ghost_output",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new GhostOutputBlockEntity(pos),
                            Blocks.AIR)
                            .build(null));

    private ModBlockEntities() {
    }
}
