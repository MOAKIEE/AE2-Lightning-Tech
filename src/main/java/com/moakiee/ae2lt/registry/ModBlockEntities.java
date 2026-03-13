package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.HighVoltageAggregatorBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2LightningTech.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HighVoltageAggregatorBlockEntity>>
            HIGH_VOLTAGE_AGGREGATOR = BLOCK_ENTITY_TYPES.register(
                    "high_voltage_aggregator",
                    () -> BlockEntityType.Builder.of(
                            HighVoltageAggregatorBlockEntity::new,
                            ModBlocks.HIGH_VOLTAGE_AGGREGATOR.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedControllerBlockEntity>>
            OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_controller",
                    () -> BlockEntityType.Builder.of(
                            OverloadedControllerBlockEntity::new,
                            ModBlocks.OVERLOADED_CONTROLLER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedPatternProviderBlockEntity>>
            OVERLOADED_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_pattern_provider",
                    () -> BlockEntityType.Builder.of(
                            OverloadedPatternProviderBlockEntity::new,
                            ModBlocks.OVERLOADED_PATTERN_PROVIDER.get())
                            .build(null));

    private ModBlockEntities() {
    }
}
