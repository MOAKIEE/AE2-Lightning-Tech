package com.moakiee.ae2lt.client;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;

public class FumoBlockRenderState extends BlockEntityRenderState {
    public final BlockModelRenderState modelRenderState = new BlockModelRenderState();
    public boolean spinning;
    public float yRot;
    public BlockState renderState;
}
