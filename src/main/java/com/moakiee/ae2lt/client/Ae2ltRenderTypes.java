package com.moakiee.ae2lt.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Custom RenderType accessors for ae2lt overlays.
 */
public final class Ae2ltRenderTypes {
    private static final RenderType FACE_SEE_THROUGH = RenderType.create(
            "ae2lt_face_see_through",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
                    .bufferSize(RenderType.BIG_BUFFER_SIZE)
                    .createRenderSetup());

    private Ae2ltRenderTypes() {
    }

    public static RenderType getFaceSeeThrough() {
        return FACE_SEE_THROUGH;
    }
}
