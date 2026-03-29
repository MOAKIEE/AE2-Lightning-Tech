package com.moakiee.ae2lt.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ProgressBar.Direction;

import com.moakiee.ae2lt.menu.LightningCollectorMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LightningCollectorScreen extends AEBaseScreen<LightningCollectorMenu> {
    private final ProgressBar progressBar;

    public LightningCollectorScreen(
            LightningCollectorMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.progressBar = new ProgressBar(menu, style.getImage("progressBar"), Direction.VERTICAL);
        widgets.add("progressBar", this.progressBar);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int progress = menu.getCurrentProgress() * 100 / menu.getMaxProgress();
        this.progressBar.setFullMsg(Component.literal(progress + "%"));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        int textColor = 0x404040;
        float textScale = 0.85F;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(textScale, textScale, 1.0F);
        int textX = Math.round(8 / textScale);
        guiGraphics.drawString(
                font,
                Component.translatable(
                        "gui.ae2lt.lightning_collector.high_output",
                        formatRange(menu.previewHighMin, menu.previewHighMax)),
                textX,
                Math.round(30 / textScale),
                textColor,
                false);
        guiGraphics.drawString(
                font,
                Component.translatable(
                        "gui.ae2lt.lightning_collector.extreme_output.simple",
                        formatRange(menu.previewExtremeMin, menu.previewExtremeMax)),
                textX,
                Math.round(42 / textScale),
                textColor,
                false);
        guiGraphics.pose().popPose();
    }

    private static String formatRange(int min, int max) {
        return min == max ? Integer.toString(min) : min + "-" + max;
    }
}
