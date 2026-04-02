package com.moakiee.ae2lt.client;

import org.joml.Matrix4f;

import com.moakiee.ae2lt.me.key.LightningKey;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.client.AEKeyRenderHandler;

public final class LightningKeyRenderHandler implements AEKeyRenderHandler<LightningKey> {
    public static final LightningKeyRenderHandler INSTANCE = new LightningKeyRenderHandler();
    private static final ItemStack ICON_STACK = new ItemStack(Items.LIGHTNING_ROD);

    private LightningKeyRenderHandler() {
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, LightningKey stack) {
        guiGraphics.renderItem(ICON_STACK, x, y);
        guiGraphics.renderItemDecorations(minecraft.font, ICON_STACK, x, y, "");
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, LightningKey what, float scale,
            int combinedLight, Level level) {
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);
        poseStack.mulPose(new Matrix4f().scale(scale, scale, 0.001f));
        poseStack.last().normal().rotateX(Mth.DEG_TO_RAD * -45f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                ICON_STACK,
                ItemDisplayContext.GUI,
                combinedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffers,
                level,
                0);

        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(LightningKey stack) {
        return stack.getDisplayName();
    }
}
