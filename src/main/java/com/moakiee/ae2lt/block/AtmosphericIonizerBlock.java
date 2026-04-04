package com.moakiee.ae2lt.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;

public class AtmosphericIonizerBlock extends AEBaseEntityBlock<AtmosphericIonizerBlockEntity>
        implements SimpleWaterloggedBlock {
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_DOWN = Shapes.create(new AABB(3.0 / 16.0, 5.0 / 16.0, 3.0 / 16.0, 13.0 / 16.0, 1.0, 13.0 / 16.0));
    private static final VoxelShape SHAPE_EAST = Shapes.create(new AABB(0.0, 3.0 / 16.0, 3.0 / 16.0, 11.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0));
    private static final VoxelShape SHAPE_NORTH = Shapes.create(new AABB(3.0 / 16.0, 3.0 / 16.0, 5.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0, 1.0));
    private static final VoxelShape SHAPE_SOUTH = Shapes.create(new AABB(3.0 / 16.0, 3.0 / 16.0, 0.0, 13.0 / 16.0, 13.0 / 16.0, 11.0 / 16.0));
    private static final VoxelShape SHAPE_UP = Shapes.create(new AABB(3.0 / 16.0, 0.0, 3.0 / 16.0, 13.0 / 16.0, 11.0 / 16.0, 13.0 / 16.0));
    private static final VoxelShape SHAPE_WEST = Shapes.create(new AABB(5.0 / 16.0, 3.0 / 16.0, 3.0 / 16.0, 1.0, 13.0 / 16.0, 13.0 / 16.0));

    public AtmosphericIonizerBlock() {
        super(metalProps().noOcclusion().forceSolidOn());
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }

        var fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return state.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getVoxelShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getVoxelShape(state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction facing,
            BlockState facingState,
            LevelAccessor level,
            BlockPos currentPos,
            BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    private VoxelShape getVoxelShape(BlockState state) {
        var orientation = getOrientation(state);
        return switch (orientation.getSide(RelativeSide.FRONT)) {
            case DOWN -> SHAPE_DOWN;
            case EAST -> SHAPE_EAST;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case UP -> SHAPE_UP;
            case WEST -> SHAPE_WEST;
        };
    }
}
