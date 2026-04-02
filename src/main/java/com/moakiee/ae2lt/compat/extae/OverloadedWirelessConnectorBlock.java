package com.moakiee.ae2lt.compat.extae;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import com.glodblock.github.extendedae.common.blocks.BlockWirelessConnector;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessConnector;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Overloaded variant of ExtendedAE's Wireless Connector.
 * Inherits all block behaviour (GUI, state updates, neighbour handling)
 * from the original; the only functional change lives in the block entity
 * which is marked as an {@link com.moakiee.ae2lt.grid.OverloadedGridNodeOwner}
 * for 128-channel support.
 */
public class OverloadedWirelessConnectorBlock extends BlockWirelessConnector {

    public OverloadedWirelessConnectorBlock() {
        super();
    }

    @Override
    public ItemInteractionResult check(TileWirelessConnector tile, ItemStack stack, Level world, BlockPos thisPos,
            BlockHitResult hit, Player p) {
        return super.check(tile, stack, world, thisPos, hit, p);
    }

    @Override
    public void openGui(TileWirelessConnector tile, Player p) {
        MenuOpener.open(OverloadedWirelessConnectorMenu.TYPE, p, MenuLocators.forBlockEntity(tile));
    }
}
