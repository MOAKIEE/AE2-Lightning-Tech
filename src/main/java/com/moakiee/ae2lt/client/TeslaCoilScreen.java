package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;

public class TeslaCoilScreen extends AEBaseScreen<TeslaCoilMenu> {
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
        LargeStackCountRenderer.renderSlotCount(guiGraphics, font, slot);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);
        LargeStackCountRenderer.appendCountTooltip(lines, hoveredSlot);
        return lines;
    }
}
