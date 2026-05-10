package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import appeng.client.api.AEKeyRenderer;
import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class LightningKeyRenderHandler implements AEKeyRenderer<LightningKey, LightningKeyRenderHandler.State> {
    public static final LightningKeyRenderHandler INSTANCE = new LightningKeyRenderHandler();

    private static final Identifier HIGH_VOLTAGE_SPRITE =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "item/high_voltage_lightning");
    private static final Identifier EXTREME_HIGH_VOLTAGE_SPRITE =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "item/extreme_high_voltage_lightning");

    private LightningKeyRenderHandler() {
    }

    public static final class State {
        public TextureAtlasSprite sprite;
    }

    private static TextureAtlasSprite spriteFor(LightningKey stack) {
        Identifier id = stack.tier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? EXTREME_HIGH_VOLTAGE_SPRITE
                : HIGH_VOLTAGE_SPRITE;
        return Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(id);
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, int x, int y, LightningKey stack) {
        Blitter.sprite(spriteFor(stack))
                .dest(x, y, 16, 16)
                .blit(guiGraphics);
    }

    @Override
    public Class<State> stateClass() {
        return State.class;
    }

    @Override
    public State createState() {
        return new State();
    }

    @Override
    public void extract(State state, LightningKey what, @Nullable Level level, int seed) {
        state.sprite = spriteFor(what);
    }

    @Override
    public void submit(PoseStack poseStack, State state, SubmitNodeCollector nodes, int lightCoords) {
        var sprite = state.sprite;
        if (sprite == null) {
            return;
        }

        poseStack.pushPose();
        // Push out of the block face a bit to avoid z-fighting
        poseStack.translate(0, 0, 0.01f);

        // y is flipped here
        var x0 = -1 / 2f;
        var y0 = 1 / 2f;
        var x1 = 1 / 2f;
        var y1 = -1 / 2f;

        nodes.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sprite.atlasLocation()), (pose, buffer) -> {
            buffer.addVertex(pose, x0, y1, 0)
                    .setColor(0xFFFFFFFF)
                    .setUv(sprite.getU0(), sprite.getV1())
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(lightCoords)
                    .setNormal(0, 0, 1);
            buffer.addVertex(pose, x1, y1, 0)
                    .setColor(0xFFFFFFFF)
                    .setUv(sprite.getU1(), sprite.getV1())
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(lightCoords)
                    .setNormal(0, 0, 1);
            buffer.addVertex(pose, x1, y0, 0)
                    .setColor(0xFFFFFFFF)
                    .setUv(sprite.getU1(), sprite.getV0())
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(lightCoords)
                    .setNormal(0, 0, 1);
            buffer.addVertex(pose, x0, y0, 0)
                    .setColor(0xFFFFFFFF)
                    .setUv(sprite.getU0(), sprite.getV0())
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(lightCoords)
                    .setNormal(0, 0, 1);
        });

        poseStack.popPose();
    }

    @Override
    public java.util.List<Component> getTooltip(LightningKey stack) {
        return java.util.List.of(stack.getDisplayName());
    }
}
