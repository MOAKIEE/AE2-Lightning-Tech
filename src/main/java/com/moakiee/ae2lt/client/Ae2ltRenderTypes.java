package com.moakiee.ae2lt.client;

import appeng.client.render.AERenderPipelines;
import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Custom RenderType accessors for ae2lt overlays.
 */
public final class Ae2ltRenderTypes {
    private static final RenderType FACE_SEE_THROUGH = RenderType.create(
            "ae2lt_face_see_through",
            RenderSetup.builder(AERenderPipelines.AREA_OVERLAY_FACE.toBuilder()
                    .withLocation(Identifier.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "pipeline/face_see_through"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, false))
                    .build())
                    .bufferSize(RenderType.BIG_BUFFER_SIZE)
                    .createRenderSetup());

    private Ae2ltRenderTypes() {
    }

    public static RenderType getFaceSeeThrough() {
        return FACE_SEE_THROUGH;
    }
}
