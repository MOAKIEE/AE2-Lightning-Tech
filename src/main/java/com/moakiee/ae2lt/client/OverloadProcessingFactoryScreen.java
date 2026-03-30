package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.menu.LargeStackAppEngSlot;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;

public class OverloadProcessingFactoryScreen extends AEBaseScreen<OverloadProcessingFactoryMenu> {
    private static final float LARGE_STACK_COUNT_SCALE = 0.9F;
    private static final int INFO_X = 8;
    private static final int PARALLEL_Y = 88;
    private static final int HV_Y = 98;
    private static final int EHV_Y = 108;
    private static final int DEMAND_Y = 118;
    private static final int SUBSTITUTION_Y = 128;

    public OverloadProcessingFactoryScreen(
            OverloadProcessingFactoryMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.imageWidth = 176;
        this.imageHeight = 178;

        widgets.add("upgrades", new UpgradesPanel(
                menu.getSlots(SlotSemantics.UPGRADE),
                menu::getCompatibleUpgradeLines));
        widgets.add("energyBar", new OverloadProcessingFactoryEnergyBar(menu, style.getImage("energyBar")));
        widgets.add("inputFluidBar", new OverloadProcessingFactoryFluidWidget(
                menu::getInputFluid,
                menu::getInputTankCapacity,
                Component.translatable("ae2lt.gui.overload_factory.fluid.input.empty"),
                0xCC2E86DE));
        widgets.add("outputFluidBar", new OverloadProcessingFactoryFluidWidget(
                menu::getOutputFluid,
                menu::getOutputTankCapacity,
                Component.translatable("ae2lt.gui.overload_factory.fluid.output.empty"),
                0xCC3AB36A));
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.fill(offsetX, offsetY, offsetX + imageWidth, offsetY + imageHeight, 0xFF1B1F24);
        guiGraphics.fill(offsetX + 1, offsetY + 1, offsetX + imageWidth - 1, offsetY + imageHeight - 1, 0xFF222831);
        guiGraphics.fill(offsetX + 7, offsetY + 17, offsetX + 61, offsetY + 71, 0xFF181C22);
        guiGraphics.fill(offsetX + 115, offsetY + 17, offsetX + 151, offsetY + 53, 0xFF181C22);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        super.renderSlot(guiGraphics, slot);

        if (!(slot instanceof LargeStackAppEngSlot)) {
            return;
        }

        var stack = slot.getItem();
        if (stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }

        renderLargeStackCount(guiGraphics, slot.x, slot.y, Integer.toString(stack.getCount()));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        guiGraphics.drawString(font, menu.getParallelMessage(), INFO_X, PARALLEL_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getHighVoltageMessage(), INFO_X, HV_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getExtremeHighVoltageMessage(), INFO_X, EHV_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getLightningDemandMessage(), INFO_X, DEMAND_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getSubstitutionMessage(), INFO_X, SUBSTITUTION_Y, 0x404040, false);
    }

    private void renderLargeStackCount(GuiGraphics guiGraphics, int xPos, int yPos, String text) {
        float inverseScale = 1.0F / LARGE_STACK_COUNT_SCALE;
        int drawX = (int) ((xPos + 18.0F - font.width(text) * LARGE_STACK_COUNT_SCALE) * inverseScale);
        int drawY = (int) ((yPos + 16.0F - 5.0F * LARGE_STACK_COUNT_SCALE) * inverseScale);

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        pose.scale(LARGE_STACK_COUNT_SCALE, LARGE_STACK_COUNT_SCALE, LARGE_STACK_COUNT_SCALE);
        renderLargeStackCountLabel(pose.last().pose(), font, drawX, drawY, text);
        pose.popPose();
    }

    private static void renderLargeStackCountLabel(Matrix4f matrix, Font font, int x, int y, String text) {
        RenderSystem.disableBlend();
        var buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        font.drawInBatch(text, x + 1, y + 1, 0x413f54, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        font.drawInBatch(text, x, y, 0xFFFFFF, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        buffer.endBatch();
        RenderSystem.enableBlend();
    }
}
