package com.moakiee.ae2lt.compat.extae;

import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.util.AECableType;

import com.glodblock.github.extendedae.common.tileentities.TileWirelessConnector;

public class OverloadedWirelessConnectorBlockEntity extends TileWirelessConnector
        implements OverloadedGridNodeOwner {

    public OverloadedWirelessConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        getMainNode()
                .setVisualRepresentation(ExtendedAECompat.OVERLOADED_WIRELESS_CONNECTOR.get());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }
}
