package com.moakiee.ae2lt.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;

public class LightningSimulationChamberRenderState extends BlockEntityRenderState {
    public final ItemStackRenderState[] inputs = new ItemStackRenderState[] {
            new ItemStackRenderState(),
            new ItemStackRenderState(),
            new ItemStackRenderState(),
    };
    public Direction facing = Direction.NORTH;
}
