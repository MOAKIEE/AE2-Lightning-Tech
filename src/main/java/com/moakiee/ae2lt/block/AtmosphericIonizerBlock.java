package com.moakiee.ae2lt.block;

import java.util.EnumMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;

public class AtmosphericIonizerBlock extends AEBaseEntityBlock<AtmosphericIonizerBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape UP_SHAPE = BlockShapeHelper.or(
            Block.box(0, 0, 0, 16, 8, 16),
            Block.box(3, 8, 3, 13, 9, 13),
            Block.box(5, 9, 5, 11, 16, 11),
            Block.box(4, 12, 4, 12, 13, 12),
            Block.box(4, 14, 4, 12, 15, 12),
            Block.box(5, 8, 2.5, 7, 13, 4.5),
            Block.box(9, 8, 2.5, 11, 15, 4.5),
            Block.box(9, 8, 11.5, 11, 13, 13.5),
            Block.box(5, 8, 11.5, 7, 15, 13.5),
            Block.box(11.5, 8, 5, 13.5, 13, 7),
            Block.box(11.5, 8, 9, 13.5, 15, 11),
            Block.box(2.5, 8, 9, 4.5, 13, 11),
            Block.box(2.5, 8, 5, 4.5, 15, 7),
            Block.box(6, 16, 6, 10, 20, 10),
            Block.box(6, 20, 6, 10, 24, 10));
    private static final EnumMap<Direction, VoxelShape> SHAPES = BlockShapeHelper.createAllFacingShapes(UP_SHAPE);

    public AtmosphericIonizerBlock() {
        super(metalProps().noOcclusion().forceSolidOn());
        registerDefaultState(defaultBlockState()
                .setValue(WORKING, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            net.minecraft.core.BlockPos pos,
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
