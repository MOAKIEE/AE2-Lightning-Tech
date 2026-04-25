package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;

public class FumoBlockRenderer implements BlockEntityRenderer<FumoBlockEntity> {

    private static final RandomSource RAND = RandomSource.create();

    public FumoBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FumoBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        boolean spinning = blockEntity.isSpinning();

        BlockState renderState = spinning && state.hasProperty(FumoBlock.FACING)
                ? state.setValue(FumoBlock.FACING, Direction.NORTH)
                : state;

        poseStack.pushPose();
        if (spinning) {
            poseStack.translate(0.5D, 0.0D, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(blockEntity.getRenderYRot(partialTick)));
            poseStack.translate(-0.5D, 0.0D, -0.5D);
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(renderState);
        ModelData modelData = ModelData.EMPTY;

        int color = Minecraft.getInstance().getBlockColors().getColor(renderState, null, null, 0);
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RAND.setSeed(42L);
        for (RenderType renderType : model.getRenderTypes(renderState, RAND, modelData)) {
            dispatcher.getModelRenderer().renderModel(
                    poseStack.last(),
                    buffer.getBuffer(renderType),
                    renderState,
                    model,
                    r, g, b,
                    packedLight,
                    packedOverlay,
                    modelData,
                    renderType);
        }

        poseStack.popPose();
    }
}
