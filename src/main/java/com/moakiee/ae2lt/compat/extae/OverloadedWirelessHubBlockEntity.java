package com.moakiee.ae2lt.compat.extae;

import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.util.AECableType;

import com.glodblock.github.extendedae.common.tileentities.TileWirelessHub;

public class OverloadedWirelessHubBlockEntity extends TileWirelessHub
        implements OverloadedGridNodeOwner {

    public OverloadedWirelessHubBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        getMainNode()
                .setVisualRepresentation(ExtendedAECompat.OVERLOADED_WIRELESS_HUB.get());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }
}
