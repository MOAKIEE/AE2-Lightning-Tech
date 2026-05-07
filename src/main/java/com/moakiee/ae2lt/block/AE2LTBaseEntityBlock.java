package com.moakiee.ae2lt.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;

/**
 * Bridges 1.21's split block interaction hooks back onto AE2's 1.20.1
 * {@code onActivated} entrypoint.
 */
public abstract class AE2LTBaseEntityBlock<T extends AEBaseBlockEntity> extends AEBaseEntityBlock<T> {
    protected AE2LTBaseEntityBlock(Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResult onActivated(Level level, BlockPos pos, Player player,
            InteractionHand hand, @Nullable ItemStack heldItem, BlockHitResult hit) {
        var state = level.getBlockState(pos);
        var stack = heldItem == null ? ItemStack.EMPTY : heldItem;

        if (!stack.isEmpty()) {
            var itemResult = useItemOn(stack, state, level, pos, player, hand, hit);
            if (itemResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return useWithoutItem(state, level, pos, player, hit);
            }
            return itemResult.asInteractionResult();
        }

        return useWithoutItem(state, level, pos, player, hit);
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}

