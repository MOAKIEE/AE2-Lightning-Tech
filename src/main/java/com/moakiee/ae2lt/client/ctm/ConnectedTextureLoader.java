package com.moakiee.ae2lt.client.ctm;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

/**
 * Loader for {@code "loader": "ae2lt:connected_texture"} models.
 *
 * <p>Recognised fields: {@code connection} (predicate id, default
 * {@code ae2lt:same_block}) and {@code render_type} (default translucent).
 * Textures come from the standard {@code textures} block (keys {@code base},
 * {@code ctm}).
 */
public class ConnectedTextureLoader implements IGeometryLoader<ConnectedTextureGeometry> {

    @Override
    public ConnectedTextureGeometry read(JsonObject json, JsonDeserializationContext context) {
        ResourceLocation connection = ResourceLocation.parse(
                GsonHelper.getAsString(json, "connection", "ae2lt:same_block"));
        RenderType renderType = parseRenderType(GsonHelper.getAsString(json, "render_type", "minecraft:translucent"));
        return new ConnectedTextureGeometry(connection, ChunkRenderTypeSet.of(renderType));
    }

    private static RenderType parseRenderType(String name) {
        return switch (name) {
            case "solid", "minecraft:solid" -> RenderType.solid();
            case "cutout", "minecraft:cutout" -> RenderType.cutout();
            case "cutout_mipped", "minecraft:cutout_mipped" -> RenderType.cutoutMipped();
            default -> RenderType.translucent();
        };
    }
}
