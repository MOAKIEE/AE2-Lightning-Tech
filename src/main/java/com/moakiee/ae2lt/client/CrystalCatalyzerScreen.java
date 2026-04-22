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

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.gui.LightningStatusIconWidget;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;

public class CrystalCatalyzerScreen extends AEBaseScreen<CrystalCatalyzerMenu> {
    private final ToggleButton autoExportButton;
    private final ActionButton configureOutputButton;

    public CrystalCatalyzerScreen(
            CrystalCatalyzerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.imageWidth = 176;
        this.imageHeight = 190;

        widgets.add("energyBar", new CrystalCatalyzerEnergyBar(menu, style.getImage("energyBar")));
        widgets.add("fluidBar", new CrystalCatalyzerFluidWidget(
                menu::getFluid,
                menu::getFluidCapacity));
        widgets.add("processArea", new CrystalCatalyzerProgressWidget(menu, style.getImage("processOverlay")));

        this.autoExportButton = new ToggleButton(
                Icon.AUTO_EXPORT_ON,
                Icon.AUTO_EXPORT_OFF,
                state -> menu.clientToggleAutoExport());
        this.autoExportButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.crystal_catalyzer.auto_export.title"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.auto_export.on")));
        this.autoExportButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.crystal_catalyzer.auto_export.title"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.auto_export.off")));
        addToLeftToolbar(this.autoExportButton);

        this.configureOutputButton = new ActionButton(
                ActionItems.COG,
                () -> switchToScreen(new CrystalCatalyzerOutputConfigScreen(this)));
        this.configureOutputButton.setMessage(
                Component.translatable("ae2lt.gui.crystal_catalyzer.configure_output"));
        addToLeftToolbar(this.configureOutputButton);

        widgets.add("lightningStatus", new LightningStatusIconWidget(() -> List.of(
                LightningStatusLines.title(),
                LightningStatusLines.status(menu.isWorking()),
                LightningStatusLines.progress(menu.getProgress()),
                LightningStatusLines.energy(menu.getStoredEnergy(), menu.getEnergyCapacity()))));
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
