package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;

public class OverloadedPatternProviderScreen extends PatternProviderScreen<OverloadedPatternProviderMenu> {

    private static final List<Component> RETURN_TIP_OFF =
            List.of(Component.translatable("ae2lt.gui.return_mode.off"));
    private static final List<Component> RETURN_TIP_AUTO =
            List.of(Component.translatable("ae2lt.gui.return_mode.auto"));
    private static final List<Component> RETURN_TIP_EJECT =
            List.of(Component.translatable("ae2lt.gui.return_mode.eject"));
    private static final List<Component> FILTER_TIP_ON =
            List.of(Component.translatable("ae2lt.gui.filtered_import.on"));
    private static final List<Component> FILTER_TIP_OFF =
            List.of(Component.translatable("ae2lt.gui.filtered_import.off"));

    private final TextureToggleButton modeButton;
    private final TextureToggleButton autoReturnButton;
    private final TextureToggleButton filteredImportButton;

    private static final int SLOTS_PER_PAGE = 36;

    private Button prevPageButton;
    private Button nextPageButton;

    public OverloadedPatternProviderScreen(OverloadedPatternProviderMenu menu, Inventory playerInventory,
                                           Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.autoReturnButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_RETURN,
                btn -> menu.clientToggleAutoReturn());
        addToLeftToolbar(this.autoReturnButton);

        this.modeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.MODE,
                btn -> menu.clientToggleMode());
        this.modeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.provider_mode.wireless")));
        this.modeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.provider_mode.normal")));
        addToLeftToolbar(this.modeButton);

        this.filteredImportButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.FILTERED_IMPORT,
                btn -> menu.clientToggleFilteredImport());
        this.filteredImportButton.setTooltipOn(FILTER_TIP_ON);
        this.filteredImportButton.setTooltipOff(FILTER_TIP_OFF);
        addToLeftToolbar(this.filteredImportButton);
    }

    @Override
    protected void init() {
        super.init();

        alignSlotPositions();

        prevPageButton = Button.builder(Component.literal("<"),
                btn -> this.menu.clientPrevPage())
                .bounds(this.leftPos + 110, this.topPos + 30, 14, 12).build();

        nextPageButton = Button.builder(Component.literal(">"),
                btn -> this.menu.clientNextPage())
                .bounds(this.leftPos + 156, this.topPos + 30, 14, 12).build();

        addRenderableWidget(prevPageButton);
        addRenderableWidget(nextPageButton);
    }

    /**
     * After AE2's layout system positions all ENCODED_PATTERN slots,
     * copy page-0 positions to every subsequent page so all pages
     * share the same screen coordinates. Only active/inactive toggles
     * are needed to switch pages — no per-frame coordinate remapping.
     */
    private void alignSlotPositions() {
        var patternSlots = this.menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        int total = patternSlots.size();
        if (total <= SLOTS_PER_PAGE) return;

        for (int i = SLOTS_PER_PAGE; i < total; i++) {
            int ref = i % SLOTS_PER_PAGE;
            patternSlots.get(i).x = patternSlots.get(ref).x;
            patternSlots.get(i).y = patternSlots.get(ref).y;
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        int tp = this.menu.getTotalPages();
        if (tp > 1) {
            String pageText = (this.menu.getCurrentPage() + 1) + "/" + tp;
            int textWidth = this.font.width(pageText);
            guiGraphics.drawString(this.font, pageText, 136 - textWidth / 2, 33, 0x404040, false);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.menu.showPage(this.menu.getCurrentPage());

        this.modeButton.setState(this.menu.isWirelessMode());

        int rm = this.menu.getReturnModeOrdinal();
        this.autoReturnButton.setState(rm != ReturnMode.OFF.ordinal());
        if (rm == ReturnMode.OFF.ordinal()) {
            this.autoReturnButton.setTooltipOff(RETURN_TIP_OFF);
            this.autoReturnButton.setTooltipOn(RETURN_TIP_AUTO);
        } else if (rm == ReturnMode.AUTO.ordinal()) {
            this.autoReturnButton.setTooltipOn(RETURN_TIP_AUTO);
        } else {
            this.autoReturnButton.setTooltipOn(RETURN_TIP_EJECT);
        }

        this.filteredImportButton.setState(this.menu.isFilteredImport());

        boolean multiPage = this.menu.getTotalPages() > 1;
        prevPageButton.visible = multiPage;
        nextPageButton.visible = multiPage;
        prevPageButton.active = multiPage && this.menu.getCurrentPage() > 0;
        nextPageButton.active = multiPage && this.menu.getCurrentPage() < this.menu.getTotalPages() - 1;
    }

}
