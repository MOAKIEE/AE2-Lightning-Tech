package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.ActionItems;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;

public class OverloadProcessingFactoryScreen extends AEBaseScreen<OverloadProcessingFactoryMenu> {
    private static final int INFO_X = 8;
    private static final int PARALLEL_Y = 88;
    private static final int HV_Y = 98;
    private static final int EHV_Y = 108;
    private static final int DEMAND_Y = 118;
    private static final int SUBSTITUTION_Y = 128;

    private final ToggleButton autoExportButton;
    private final ActionButton configureOutputButton;

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

        this.autoExportButton = new ToggleButton(
                Icon.AUTO_EXPORT_ON,
                Icon.AUTO_EXPORT_OFF,
                state -> menu.clientToggleAutoExport());
        this.autoExportButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.overload_factory.auto_export.title"),
                Component.translatable("ae2lt.gui.overload_factory.auto_export.on")));
        this.autoExportButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.overload_factory.auto_export.title"),
                Component.translatable("ae2lt.gui.overload_factory.auto_export.off")));
        addToLeftToolbar(this.autoExportButton);

        this.configureOutputButton = new ActionButton(
                ActionItems.COG,
                () -> switchToScreen(new OverloadProcessingFactoryOutputConfigScreen(this)));
        this.configureOutputButton.setMessage(
                Component.translatable("ae2lt.gui.overload_factory.configure_output"));
        addToLeftToolbar(this.configureOutputButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.autoExportButton.setState(menu.isAutoExportEnabled());
        this.configureOutputButton.setVisibility(menu.isAutoExportEnabled());
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.fill(offsetX, offsetY, offsetX + imageWidth, offsetY + imageHeight, 0xFF1B1F24);
        guiGraphics.fill(offsetX + 1, offsetY + 1, offsetX + imageWidth - 1, offsetY + imageHeight - 1, 0xFF222831);
        guiGraphics.fill(offsetX + 7, offsetY + 17, offsetX + 63, offsetY + 73, 0xFF181C22);
        guiGraphics.fill(offsetX + 62, offsetY + 17, offsetX + 80, offsetY + 77, 0xFF181C22);
        guiGraphics.fill(offsetX + 129, offsetY + 17, offsetX + 167, offsetY + 77, 0xFF181C22);
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

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        guiGraphics.drawString(font, menu.getParallelMessage(), INFO_X, PARALLEL_Y, 0xBBBBBB, false);
        guiGraphics.drawString(font, menu.getHighVoltageMessage(), INFO_X, HV_Y, 0xBBBBBB, false);
        guiGraphics.drawString(font, menu.getExtremeHighVoltageMessage(), INFO_X, EHV_Y, 0xBBBBBB, false);
        guiGraphics.drawString(font, menu.getLightningDemandMessage(), INFO_X, DEMAND_Y, 0xBBBBBB, false);
        guiGraphics.drawString(font, menu.getSubstitutionMessage(), INFO_X, SUBSTITUTION_Y, 0xBBBBBB, false);
    }
}
