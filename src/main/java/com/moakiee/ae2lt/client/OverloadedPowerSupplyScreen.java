package com.moakiee.ae2lt.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.menu.OverloadedPowerSupplyMenu;

public class OverloadedPowerSupplyScreen extends AEBaseScreen<OverloadedPowerSupplyMenu> {

    private static final int INFO_X = 8;
    private static final int CONNECTIONS_Y = 30;
    private static final int BUFFER_Y = 42;
    private static final int TICKETS_Y = 54;
    private static final int CELL_Y = 66;
    private static final int STATUS_Y = 78;

    private Button modeButton;

    public OverloadedPowerSupplyScreen(OverloadedPowerSupplyMenu menu, Inventory playerInventory,
                                       Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
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
            modeButton.setMessage(menu.getModeButtonMessage());
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        guiGraphics.drawString(font, menu.getConnectionsMessage(), INFO_X, CONNECTIONS_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getBufferMessage(), INFO_X, BUFFER_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getTicketsMessage(), INFO_X, TICKETS_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getCellMessage(), INFO_X, CELL_Y, 0x404040, false);
        guiGraphics.drawString(font, menu.getStatusMessage(), INFO_X, STATUS_Y, 0x404040, false);
    }
}
