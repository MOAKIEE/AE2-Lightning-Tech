package com.moakiee.ae2lt.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class AtmosphericIonizerScreen extends AEBaseScreen<AtmosphericIonizerMenu> {
    private final AtmosphericIonizerEnergyBar energyBar;

    public AtmosphericIonizerScreen(
            AtmosphericIonizerMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.energyBar = new AtmosphericIonizerEnergyBar(menu, style.getImage("energyBar"));
        widgets.add("energyBar", energyBar);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        int textColor = 0x404040;
        guiGraphics.drawString(font, menu.getStatusMessage(), 28, 30, textColor, false);
        guiGraphics.drawString(font, menu.getTargetWeatherMessage(), 28, 42, textColor, false);
        guiGraphics.drawString(font, menu.getEnergyDemandMessage(), 28, 54, textColor, false);
        guiGraphics.drawString(
                font,
                Component.translatable("ae2lt.gui.atmospheric_ionizer.progress", Math.round(menu.getProgress() * 100.0D)),
                28,
                66,
                textColor,
                false);
    }
}
