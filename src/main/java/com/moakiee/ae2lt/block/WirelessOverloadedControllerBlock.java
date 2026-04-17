package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.menu.FrequencyMenu;

/**
 * Block for the Wireless Overloaded Controller (normal version).
 * Opens the frequency management GUI on right-click.
 */
public class WirelessOverloadedControllerBlock extends OverloadedControllerBlock {

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WirelessOverloadedControllerBlockEntity be) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, p) -> new FrequencyMenu(id, inv, be),
                        be.getBlockState().getBlock().getName()
                ), buf -> FrequencyMenu.writeExtraData(buf, be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useWithoutItem(state, level, pos, player, hitResult);
    }
}
