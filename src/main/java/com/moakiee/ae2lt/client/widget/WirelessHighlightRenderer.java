package com.moakiee.ae2lt.client.widget;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.moakiee.ae2lt.AE2LightningTech;

import java.util.OptionalDouble;

/**
 * Renders a temporary highlight box around a block in the world.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class WirelessHighlightRenderer {

    private static final RenderType HIGHLIGHT_LINES = RenderType.create(
            "ae2lt_wireless_highlight",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    @Nullable
    private static BlockPos highlightPos;
    @Nullable
    private static ResourceKey<Level> highlightDim;
    private static long highlightEndTime;

    private WirelessHighlightRenderer() {
    }

    public static void highlight(BlockPos pos, ResourceKey<Level> dimension, long durationMs) {
        highlightPos = pos;
        highlightDim = dimension;
        highlightEndTime = System.currentTimeMillis() + durationMs;
    }

    public static void clear() {
        highlightPos = null;
        highlightDim = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (highlightPos == null || highlightDim == null) {
            return;
        }
        if (System.currentTimeMillis() > highlightEndTime) {
            clear();
            return;
        }

        var mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(highlightDim)) {
            return;
        }

        // Calculate alpha based on remaining time
        long remaining = highlightEndTime - System.currentTimeMillis();
        float alpha = Math.min(1.0F, remaining / 1000.0F);
        alpha = Math.max(0.3F, alpha);

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        AABB box = new AABB(highlightPos).inflate(0.002);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(HIGHLIGHT_LINES);

        renderBox(poseStack, buffer, box, 0.3F, 1.0F, 0.3F, alpha);

        bufferSource.endBatch(HIGHLIGHT_LINES);
        poseStack.popPose();
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer buffer, AABB box,
            float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        // Bottom face edges
        line(buffer, matrix, x0, y0, z0, x1, y0, z0, r, g, b, a, 1, 0, 0);
        line(buffer, matrix, x1, y0, z0, x1, y0, z1, r, g, b, a, 0, 0, 1);
        line(buffer, matrix, x1, y0, z1, x0, y0, z1, r, g, b, a, -1, 0, 0);
        line(buffer, matrix, x0, y0, z1, x0, y0, z0, r, g, b, a, 0, 0, -1);

        // Top face edges
        line(buffer, matrix, x0, y1, z0, x1, y1, z0, r, g, b, a, 1, 0, 0);
        line(buffer, matrix, x1, y1, z0, x1, y1, z1, r, g, b, a, 0, 0, 1);
        line(buffer, matrix, x1, y1, z1, x0, y1, z1, r, g, b, a, -1, 0, 0);
        line(buffer, matrix, x0, y1, z1, x0, y1, z0, r, g, b, a, 0, 0, -1);

        // Vertical edges
        line(buffer, matrix, x0, y0, z0, x0, y1, z0, r, g, b, a, 0, 1, 0);
        line(buffer, matrix, x1, y0, z0, x1, y1, z0, r, g, b, a, 0, 1, 0);
        line(buffer, matrix, x1, y0, z1, x1, y1, z1, r, g, b, a, 0, 1, 0);
        line(buffer, matrix, x0, y0, z1, x0, y1, z1, r, g, b, a, 0, 1, 0);
    }

    private static void line(VertexConsumer buffer, Matrix4f matrix,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float b, float a,
            float nx, float ny, float nz) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }
}
