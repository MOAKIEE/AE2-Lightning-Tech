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
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.block.LightningSimulationChamberBlock;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;

public class LightningSimulationChamberRenderer
        implements BlockEntityRenderer<LightningSimulationChamberBlockEntity, LightningSimulationChamberRenderState> {
    private static final float ITEM_SCALE = 0.35F;
    private static final float ITEM_BASE_HEIGHT = 2.05F / 16.0F;
    private static final float ITEM_LAYER_OFFSET = 0.01F;
    private static final float ITEM_DEPTH = 0.50F;
    private static final float[] INPUT_X_POSITIONS = { 0.34F, 0.50F, 0.66F };

    private final ItemModelResolver itemModelResolver;

    public LightningSimulationChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public LightningSimulationChamberRenderState createRenderState() {
        return new LightningSimulationChamberRenderState();
    }

    @Override
    public void extractRenderState(LightningSimulationChamberBlockEntity be,
            LightningSimulationChamberRenderState state, float partialTicks, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPos, crumblingOverlay);

        var blockState = be.getBlockState();
        state.facing = blockState.hasProperty(LightningSimulationChamberBlock.FACING)
                ? blockState.getValue(LightningSimulationChamberBlock.FACING)
                : Direction.NORTH;

        var inv = be.getInventory();
        for (int i = 0; i < state.inputs.length; i++) {
            state.inputs[i].clear();
            var stack = inv.getStackInSlot(LightningSimulationChamberInventory.SLOT_INPUT_0 + i);
            if (!stack.isEmpty()) {
                itemModelResolver.updateForTopItem(state.inputs[i], stack, ItemDisplayContext.FIXED, be.getLevel(),
                        null, 0);
            }
        }
    }

    @Override
    public void submit(LightningSimulationChamberRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
            CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.facing.toYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        for (int i = 0; i < state.inputs.length; i++) {
            var item = state.inputs[i];
            if (item.isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(INPUT_X_POSITIONS[i], ITEM_BASE_HEIGHT + ITEM_LAYER_OFFSET * i, ITEM_DEPTH);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            item.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}
