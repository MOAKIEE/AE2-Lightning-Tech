package com.moakiee.ae2lt.block;

import com.mojang.serialization.MapCodec;
import com.moakiee.ae2lt.blockentity.HighVoltageAggregatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class HighVoltageAggregatorBlock extends BaseEntityBlock {
    public static final MapCodec<HighVoltageAggregatorBlock> CODEC = simpleCodec(HighVoltageAggregatorBlock::new);

    public HighVoltageAggregatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HighVoltageAggregatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(
                blockEntityType,
                com.moakiee.ae2lt.registry.ModBlockEntities.HIGH_VOLTAGE_AGGREGATOR.get(),
                HighVoltageAggregatorBlockEntity::serverTick);
    }
}
