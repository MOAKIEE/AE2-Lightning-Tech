package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.OverloadedWirelessHubBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class OverloadedWirelessHubBlock extends AEBaseEntityBlock<OverloadedWirelessHubBlockEntity> {

    public OverloadedWirelessHubBlock() {
        super(metalProps());
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof OverloadedWirelessConnectorItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
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
