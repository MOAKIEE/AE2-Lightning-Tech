package com.moakiee.ae2lt.compat.extae;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import com.glodblock.github.extendedae.common.blocks.BlockWirelessHub;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessHub;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Overloaded variant of ExtendedAE's Wireless Hub.
 * Same approach as {@link OverloadedWirelessConnectorBlock}.
 */
public class OverloadedWirelessHubBlock extends BlockWirelessHub {

    public OverloadedWirelessHubBlock() {
        super();
    }

    @Override
    public ItemInteractionResult check(TileWirelessHub tile, ItemStack stack, Level world, BlockPos thisPos,
            BlockHitResult hit, Player p) {
        return super.check(tile, stack, world, thisPos, hit, p);
    }

    @Override
    public void openGui(TileWirelessHub tile, Player p) {
        MenuOpener.open(OverloadedWirelessHubMenu.TYPE, p, MenuLocators.forBlockEntity(tile));
    }
}
