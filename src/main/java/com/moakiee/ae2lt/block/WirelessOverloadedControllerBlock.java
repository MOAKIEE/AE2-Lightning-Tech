package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.menu.WirelessControllerMenu;

/**
 * Block for the Wireless Overloaded Controller (normal version).
 * Opens the wireless controller menu (with ID Card slot) on right-click.
 */
public class WirelessOverloadedControllerBlock extends OverloadedControllerBlock {

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WirelessOverloadedControllerBlockEntity be) {
            if (!level.isClientSide) {
                MenuOpener.open(WirelessControllerMenu.TYPE, player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useWithoutItem(state, level, pos, player, hitResult);
    }
}
