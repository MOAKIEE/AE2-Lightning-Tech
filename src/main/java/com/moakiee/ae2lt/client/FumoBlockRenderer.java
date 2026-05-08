package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;

public class FumoBlockRenderer implements BlockEntityRenderer<FumoBlockEntity, FumoBlockRenderState> {

    public FumoBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public FumoBlockRenderState createRenderState() {
        return new FumoBlockRenderState();
    }

    @Override
    public void extractRenderState(FumoBlockEntity be, FumoBlockRenderState state, float partialTicks, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPos, crumblingOverlay);

        var blockState = be.getBlockState();
        state.spinning = be.isSpinning();
        state.yRot = state.spinning ? be.getRenderYRot(partialTicks) : 0.0F;
        // While spinning, render the model facing NORTH so we can apply Y rotation freely.
        state.renderState = state.spinning && blockState.hasProperty(FumoBlock.FACING)
                ? blockState.setValue(FumoBlock.FACING, Direction.NORTH)
                : blockState;

        var level = be.getLevel();
        if (level == null) {
            return;
        }

        var modelManager = Minecraft.getInstance().getModelManager();
        var model = modelManager.getBlockStateModelSet().get(state.renderState);
        var blockAndTintGetter = (BlockAndTintGetter) level;
        var pos = be.getBlockPos();
        var parts = state.modelRenderState.setupModel(new Matrix4f(),
                model.hasMaterialFlag(blockAndTintGetter, pos, state.renderState, BakedQuad.FLAG_TRANSLUCENT));
        model.collectParts(blockAndTintGetter, pos, state.renderState,
                state.modelRenderState.scratchRandomSource(42L), parts);
    }

    @Override
    public void submit(FumoBlockRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
            CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        if (state.spinning) {
            poseStack.translate(0.5D, 0.0D, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));
            poseStack.translate(-0.5D, 0.0D, -0.5D);
        }

        state.modelRenderState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
