package com.moakiee.ae2lt.block;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class LightningCollectorBlock extends AEBaseEntityBlock<LightningCollectorBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public LightningCollectorBlock() {
        super(metalProps().noOcclusion());
        registerDefaultState(defaultBlockState().setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            blockEntity.openMenu(player, MenuLocators.forBlockEntity(blockEntity));
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
