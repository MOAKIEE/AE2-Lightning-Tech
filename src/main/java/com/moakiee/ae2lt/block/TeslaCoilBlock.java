package com.moakiee.ae2lt.block;

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

import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;

public class TeslaCoilBlock extends AEBaseEntityBlock<TeslaCoilBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = BlockShapeHelper.or(
            Block.box(0, 0, 0, 16, 9, 16),
            Block.box(7, 8, 1, 9, 18, 6),
            Block.box(10, 8, 7, 15, 18, 9),
            Block.box(7, 8, 10, 9, 18, 15),
            Block.box(1, 8, 7, 6, 18, 9),
            Block.box(5, 9, 5, 11, 11, 11),
            Block.box(5.5, 11, 5.5, 10.5, 25, 10.5),
            Block.box(5, 18, 5, 11, 23, 11),
            Block.box(5, 23, 5, 11, 24, 11),
            Block.box(5, 25, 5, 11, 26, 11),
            Block.box(6, 26, 6, 10, 30, 10),
            Block.box(1, 28, 1, 12, 31, 4),
            Block.box(1, 28, 4, 4, 31, 15),
            Block.box(4, 28, 12, 15, 31, 15),
            Block.box(12, 28, 1, 15, 31, 12),
            Block.box(7.5, 26, 1, 8.5, 28, 6),
            Block.box(10, 26, 7.5, 15, 28, 8.5),
            Block.box(7.5, 26, 10, 8.5, 28, 15),
            Block.box(1, 26, 7.5, 6, 28, 8.5));

    public TeslaCoilBlock() {
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
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player, BlockHitResult hitResult) {
        var be = getBlockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
