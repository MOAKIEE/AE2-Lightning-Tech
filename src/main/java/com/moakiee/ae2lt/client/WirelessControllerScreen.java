package com.moakiee.ae2lt.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.menu.WirelessControllerMenu;

public class WirelessControllerScreen extends AEBaseScreen<WirelessControllerMenu> {

    private static final int INFO_X = 8;
    private static final int UUID_Y = 30;
    private static final int TYPE_Y = 42;

    public WirelessControllerScreen(WirelessControllerMenu menu, Inventory playerInventory,
                                    Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        String uuid = menu.getUuidShort();
        Component uuidLine;
        if (uuid.isEmpty()) {
            uuidLine = Component.translatable("ae2lt.gui.wireless_controller.no_card")
                    .withStyle(ChatFormatting.GRAY);
        } else {
            uuidLine = Component.translatable("ae2lt.gui.wireless_controller.uuid", uuid)
                    .withStyle(ChatFormatting.AQUA);
        }
        guiGraphics.drawString(font, uuidLine, INFO_X, UUID_Y, 0x404040, false);

        Component typeLine;
        if (menu.isAdvanced()) {
            typeLine = Component.translatable("ae2lt.gui.wireless_controller.advanced")
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        } else {
            typeLine = Component.translatable("ae2lt.gui.wireless_controller.normal")
                    .withStyle(ChatFormatting.WHITE);
        }
        guiGraphics.drawString(font, typeLine, INFO_X, TYPE_Y, 0x404040, false);
    }
}
