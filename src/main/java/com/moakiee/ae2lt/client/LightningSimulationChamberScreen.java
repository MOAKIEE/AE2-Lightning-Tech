package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.config.ActionItems;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.menu.LargeStackAppEngSlot;

public class LightningSimulationChamberScreen extends AEBaseScreen<LightningSimulationChamberMenu> {
    private static final float LARGE_STACK_COUNT_SCALE = 0.9F;

    private final LightningSimulationEnergyBar energyBar;
    private final LightningSimulationProcessWidget processWidget;
    private final ToggleButton autoExportButton;
    private final ActionButton configureOutputButton;

    public LightningSimulationChamberScreen(
            LightningSimulationChamberMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        widgets.add("upgrades", new UpgradesPanel(
                menu.getSlots(SlotSemantics.UPGRADE),
                this::getCompatibleUpgrades));

        this.processWidget = new LightningSimulationProcessWidget(menu, style.getImage("processOverlay"));
        widgets.add("processArea", processWidget);

        this.energyBar = new LightningSimulationEnergyBar(menu, style.getImage("energyBar"));
        widgets.add("energyBar", energyBar);

        this.autoExportButton = new ToggleButton(
                Icon.AUTO_EXPORT_ON,
                Icon.AUTO_EXPORT_OFF,
                state -> menu.clientToggleAutoExport());
        this.autoExportButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.lightning_simulation.auto_export.title"),
                Component.translatable("ae2lt.gui.lightning_simulation.auto_export.on")));
        this.autoExportButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.lightning_simulation.auto_export.title"),
                Component.translatable("ae2lt.gui.lightning_simulation.auto_export.off")));
        addToLeftToolbar(this.autoExportButton);

        this.configureOutputButton = new ActionButton(
                ActionItems.COG,
                () -> switchToScreen(new LightningSimulationOutputConfigScreen(this)));
        this.configureOutputButton.setMessage(
                Component.translatable("ae2lt.gui.lightning_simulation.configure_output"));
        addToLeftToolbar(this.configureOutputButton);
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

        renderLargeStackCount(guiGraphics, slot.x, slot.y, formatLargeStackCount(stack.getCount()));
    }

    private List<Component> getCompatibleUpgrades() {
        var list = new ArrayList<Component>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(Upgrades.getTooltipLinesForMachine(menu.getHost().getUpgrades().getUpgradableItem()));
        return list;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.autoExportButton.setState(menu.isAutoExportEnabled());
        this.configureOutputButton.setVisibility(menu.isAutoExportEnabled());
    }

    private static String formatLargeStackCount(int count) {
        return Integer.toString(count);
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
