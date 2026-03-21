package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.ExtendedPatternProviderBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 144-slot extended variant — reuses all logic from {@link OverloadedPatternProviderBlock},
 * only overrides BlockEntity creation to use {@link ExtendedPatternProviderBlockEntity}.
 */
public class ExtendedPatternProviderBlock extends OverloadedPatternProviderBlock {

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExtendedPatternProviderBlockEntity(pos, state);
    }
}
