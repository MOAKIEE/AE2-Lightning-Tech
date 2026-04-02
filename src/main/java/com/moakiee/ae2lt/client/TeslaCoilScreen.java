package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.menu.LargeStackAppEngSlot;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;

public class TeslaCoilScreen extends AEBaseScreen<TeslaCoilMenu> {
    private static final float LARGE_STACK_COUNT_SCALE = 0.9F;
    private static final int INFO_X = 58;
    private static final int STATUS_Y = 64;
    private static final int MATRIX_Y = 73;
    private static final int HIGH_VOLTAGE_Y = 82;
    private static final int EXTREME_HIGH_VOLTAGE_Y = 91;

    private final TeslaCoilEnergyBar energyBar;
    private Button modeButton;

    public TeslaCoilScreen(TeslaCoilMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.energyBar = new TeslaCoilEnergyBar(menu, style.getImage("energyBar"));
        widgets.add("energyBar", energyBar);
    }

    @Override
    protected void init() {
        super.init();

        this.modeButton = Button.builder(Component.empty(), button -> menu.clientCycleMode())
                .bounds(leftPos + 56, topPos + 4, 86, 16)
                .build();
        addRenderableWidget(modeButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (modeButton != null) {
            modeButton.active = !menu.isModeLocked();
            modeButton.setMessage(menu.getModeButtonMessage());
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        guiGraphics.drawString(font, menu.getStatusMessage(), INFO_X, STATUS_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getMatrixMessage(), INFO_X, MATRIX_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getHighVoltageMessage(), INFO_X, HIGH_VOLTAGE_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getExtremeHighVoltageMessage(), INFO_X, EXTREME_HIGH_VOLTAGE_Y, 0x404040, false);
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
