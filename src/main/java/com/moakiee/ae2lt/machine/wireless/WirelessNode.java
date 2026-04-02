package com.moakiee.ae2lt.machine.wireless;

import appeng.api.networking.IGridNode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface WirelessNode {

    long getFrequency();

    Level getLevel();

    BlockPos getBlockPos();

    IGridNode getGridNode();

    BlockEntity getBlockEntity();
}
