package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;

public class CrystalCatalyzerRenderer
        implements BlockEntityRenderer<CrystalCatalyzerBlockEntity, CrystalCatalyzerRenderState> {
    private static final double CAVITY_CENTER_Y = 8.0D / 16.0D;
    private static final float ITEM_SCALE = 0.50F;
    private static final float WORKING_ROTATION_SPEED = 4.0F;

    private final ItemModelResolver itemModelResolver;

    public CrystalCatalyzerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public CrystalCatalyzerRenderState createRenderState() {
        return new CrystalCatalyzerRenderState();
    }

    @Override
    public void extractRenderState(CrystalCatalyzerBlockEntity be, CrystalCatalyzerRenderState state, float partialTicks,
            Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPos, crumblingOverlay);

        state.item.clear();
        var stack = be.getInventory().getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST);
        if (stack.isEmpty()) {
            state.rotationDegrees = 0.0F;
            return;
        }

        itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, be.getLevel(), null, 0);

        var level = be.getLevel();
        if (level != null && be.isWorking()) {
            state.rotationDegrees = (level.getGameTime() + partialTicks) * WORKING_ROTATION_SPEED;
        } else {
            state.rotationDegrees = 0.0F;
        }
    }

    @Override
    public void submit(CrystalCatalyzerRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
            CameraRenderState cameraRenderState) {
        if (state.item.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, CAVITY_CENTER_Y, 0.5D);

        if (state.rotationDegrees != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(state.rotationDegrees));
        }

        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        state.item.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
