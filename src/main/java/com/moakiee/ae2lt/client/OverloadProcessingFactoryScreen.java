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
        widgets.add("processArea", new OverloadProcessingFactoryProgressWidget(menu, style.getImage("processOverlay")));
        widgets.add("energyBar", new OverloadProcessingFactoryEnergyBar(menu, style.getImage("energyBar")));
        widgets.add("inputFluidBar", new OverloadProcessingFactoryFluidWidget(
                menu::getInputFluid,
                menu::getInputTankCapacity,
                0xCC2E86DE));
        widgets.add("outputFluidBar", new OverloadProcessingFactoryFluidWidget(
                menu::getOutputFluid,
                menu::getOutputTankCapacity,
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
