package com.moakiee.ae2lt.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.moakiee.ae2lt.registry.ModBlockEntities;

/**
 * 144-slot (4-page) variant of the Overloaded Pattern Provider for testing.
 */
public class ExtendedPatternProviderBlockEntity extends OverloadedPatternProviderBlockEntity {

    public static final int PATTERN_PAGES = 4;
    public static final int TOTAL_CAPACITY = PATTERN_PAGES * SLOTS_PER_PAGE;

    public ExtendedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.EXTENDED_PATTERN_PROVIDER.get(), pos, blockState);
    }

    @Override
    public int getTotalPatternCapacity() {
        return TOTAL_CAPACITY;
    }
}
