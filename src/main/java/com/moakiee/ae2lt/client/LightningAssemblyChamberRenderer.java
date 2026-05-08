package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;

public class LightningAssemblyChamberRenderer
        implements BlockEntityRenderer<LightningAssemblyChamberBlockEntity, LightningAssemblyChamberRenderState> {

    private final ItemModelResolver itemModelResolver;

    public LightningAssemblyChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public LightningAssemblyChamberRenderState createRenderState() {
        return new LightningAssemblyChamberRenderState();
    }

    @Override
    public void extractRenderState(LightningAssemblyChamberBlockEntity be, LightningAssemblyChamberRenderState state,
            float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPos, crumblingOverlay);

        state.item.clear();
        var stack = be.getClientRecipeResult();
        if (stack.isEmpty()) {
            return;
        }

        state.isBlockItem = stack.getItem() instanceof BlockItem;
        itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND, be.getLevel(), null, 0);
    }

    @Override
    public void submit(LightningAssemblyChamberRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
            CameraRenderState cameraRenderState) {
        if (state.item.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        if (state.isBlockItem) {
            poseStack.translate(0, -0.2F, 0);
        } else {
            poseStack.translate(0, -0.3F, 0);
        }

        state.item.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
