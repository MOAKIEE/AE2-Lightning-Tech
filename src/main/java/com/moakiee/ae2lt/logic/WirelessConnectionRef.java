package com.moakiee.ae2lt.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;

public interface WirelessConnectionRef {
    ResourceKey<Level> dimension();

    BlockPos pos();

    Direction boundFace();

    void writeTo(ValueOutput output);

    default boolean sameTarget(ResourceKey<Level> otherDim, BlockPos otherPos) {
        return dimension().equals(otherDim) && pos().equals(otherPos);
    }
}
