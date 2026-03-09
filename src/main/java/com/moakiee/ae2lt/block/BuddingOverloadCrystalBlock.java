package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;

public class BuddingOverloadCrystalBlock extends Block {
    public static final int GROWTH_CHANCE = 5;
    public static final int DECAY_CHANCE = 12;

    private static final Direction[] DIRECTIONS = Direction.values();

    private final GrowthStage stage;

    public BuddingOverloadCrystalBlock(GrowthStage stage, Properties properties) {
        super(properties);
        this.stage = stage;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROWTH_CHANCE) != 0) {
            return;
        }

        var direction = Util.getRandom(DIRECTIONS, random);
        var targetPos = pos.relative(direction);
        var targetState = level.getBlockState(targetPos);
        Block newCluster = null;

        if (canClusterGrowAtState(targetState)) {
            newCluster = ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get();
        }

        if (newCluster == null) {
            return;
        }

        var newClusterState = newCluster.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, targetState.getFluidState().getType() == Fluids.WATER);
        level.setBlockAndUpdate(targetPos, newClusterState);

        if (stage == GrowthStage.FLAWLESS || random.nextInt(DECAY_CHANCE) != 0) {
            return;
        }

        level.setBlockAndUpdate(pos, stage.nextBlock().defaultBlockState());
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }

    public enum GrowthStage {
        FLAWLESS,
        FLAWED,
        CRACKED,
        DAMAGED;

        public Block nextBlock() {
            return switch (this) {
                case FLAWLESS -> ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get();
                case FLAWED -> ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get();
                case CRACKED -> ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get();
                case DAMAGED -> ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get();
            };
        }
    }
}
