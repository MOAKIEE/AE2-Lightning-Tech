package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.block.AEBaseEntityBlock;

import com.moakiee.ae2lt.blockentity.WirelessTransmitterBlockEntity;

/**
 * Phase-1 single-block wireless transmitter.
 * Must be placed adjacent to a {@link WirelessIdBlock} to function.
 */
public class WirelessTransmitterBlock extends AEBaseEntityBlock<WirelessTransmitterBlockEntity> {

    public WirelessTransmitterBlock() {
        super(metalProps());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide()) {
            var be = getBlockEntity(level, pos);
            if (be != null) {
                be.onNeighborChanged();
            }
        }
    }
}
