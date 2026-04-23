package com.moakiee.ae2lt.block;

import java.util.EnumMap;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;

public class AtmosphericIonizerBlock extends AEBaseEntityBlock<AtmosphericIonizerBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape UP_SHAPE_LOWER = BlockShapeHelper.or(
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
            Block.box(2.5, 8, 5, 4.5, 15, 7));

    private static final VoxelShape UP_SHAPE_UPPER = BlockShapeHelper.or(
            Block.box(6, 0, 6, 10, 4, 10),
            Block.box(6, 4, 6, 10, 8, 10));

    private static final EnumMap<Direction, VoxelShape> SHAPES_LOWER =
            BlockShapeHelper.createAllFacingShapes(UP_SHAPE_LOWER);
    private static final EnumMap<Direction, VoxelShape> SHAPES_UPPER =
            BlockShapeHelper.createAllFacingShapes(UP_SHAPE_UPPER);

    public AtmosphericIonizerBlock() {
        super(metalProps().noOcclusion().forceSolidOn());
        registerDefaultState(defaultBlockState()
                .setValue(WORKING, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING, HALF);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        return super.newBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }

        Level level = context.getLevel();
        BlockPos extensionPos = context.getClickedPos().relative(getExtensionDirection(state.getValue(FACING)));
        if (extensionPos.getY() < level.getMinBuildHeight()
                || extensionPos.getY() >= level.getMaxBuildHeight()
                || !level.getBlockState(extensionPos).canBeReplaced(context)) {
            return null;
        }

        return state.setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            BlockState upperState = state.setValue(HALF, DoubleBlockHalf.UPPER);
            level.setBlock(pos.relative(getExtensionDirection(state.getValue(FACING))), upperState, Block.UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? SHAPES_UPPER.get(state.getValue(FACING))
                : SHAPES_LOWER.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lowerState = level.getBlockState(getLowerPos(pos, state));
            return isSameIonizerHalf(lowerState, DoubleBlockHalf.LOWER, state.getValue(FACING));
        }
        return super.canSurvive(state, level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction extensionDirection = getExtensionDirection(state.getValue(FACING));
        DoubleBlockHalf half = state.getValue(HALF);

        if (half == DoubleBlockHalf.LOWER && direction == extensionDirection) {
            if (!isSameIonizerHalf(neighborState, DoubleBlockHalf.UPPER, state.getValue(FACING))) {
                return Blocks.AIR.defaultBlockState();
            }
        } else if (half == DoubleBlockHalf.UPPER && direction == extensionDirection.getOpposite()) {
            if (!isSameIonizerHalf(neighborState, DoubleBlockHalf.LOWER, state.getValue(FACING))) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = getLowerPos(pos, state);
            BlockState lowerState = level.getBlockState(lowerPos);
            if (isSameIonizerHalf(lowerState, DoubleBlockHalf.LOWER, state.getValue(FACING))) {
                level.destroyBlock(lowerPos, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            Direction extensionDirection = getExtensionDirection(state.getValue(FACING));
            boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
            BlockPos otherPos = lower
                    ? pos.relative(extensionDirection)
                    : pos.relative(extensionDirection.getOpposite());
            DoubleBlockHalf otherHalf = lower ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER;
            BlockState otherState = level.getBlockState(otherPos);
            if (isSameIonizerHalf(otherState, otherHalf, state.getValue(FACING))) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return List.of();
        }
        return super.getDrops(state, builder);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(asItem());
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            net.minecraft.core.BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = getLowerPos(pos, state);
            BlockState lowerState = level.getBlockState(lowerPos);
            if (isSameIonizerHalf(lowerState, DoubleBlockHalf.LOWER, state.getValue(FACING))) {
                return useWithoutItem(lowerState, level, lowerPos, player, hitResult);
            }
            return InteractionResult.PASS;
        }

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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = getLowerPos(pos, state);
            BlockState lowerState = level.getBlockState(lowerPos);
            if (isSameIonizerHalf(lowerState, DoubleBlockHalf.LOWER, state.getValue(FACING))) {
                return super.useItemOn(stack, lowerState, level, lowerPos, player, hand, hit);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    private static Direction getExtensionDirection(Direction facing) {
        return facing.getAxis().isVertical() ? facing : facing.getOpposite();
    }

    private static BlockPos getLowerPos(BlockPos upperPos, BlockState upperState) {
        return upperPos.relative(getExtensionDirection(upperState.getValue(FACING)).getOpposite());
    }

    private boolean isSameIonizerHalf(BlockState state, DoubleBlockHalf half, Direction facing) {
        return state.is(this)
                && state.getValue(HALF) == half
                && state.getValue(FACING) == facing;
    }
}
