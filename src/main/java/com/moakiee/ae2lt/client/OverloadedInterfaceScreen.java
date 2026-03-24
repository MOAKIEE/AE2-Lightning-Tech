package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEItems;
import appeng.core.localization.ButtonToolTips;


public class OverloadedInterfaceScreen extends UpgradeableScreen<OverloadedInterfaceMenu> {

    private static final int SLOTS_PER_PAGE = 18;
    private static final int COLS = 9;
    private static final int SLOT_SPACING = 18;
    private static final int AMT_BTN_SIZE = 16;
    private static final int AMT_ROW1_Y = 35;
    private static final int AMT_ROW2_Y = 95;
    private static final int AMT_START_X = 8;

    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final TextureToggleButton modeButton;
    private final TextureToggleButton exportModeButton;
    private final TextureToggleButton importModeButton;
    private final TextureToggleButton speedButton;
    private final List<SetAmountButton> amountButtons = new ArrayList<>();
    private final List<Slot> configSlots;

    private Button prevPageBtn;
    private Button nextPageBtn;
    private int lastKnownPage = -1;

    public OverloadedInterfaceScreen(OverloadedInterfaceMenu menu, Inventory playerInventory,
                                     Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.fuzzyMode = new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        addToLeftToolbar(this.fuzzyMode);

        this.modeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.MODE, btn -> menu.cycleInterfaceMode());
        this.modeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.interface_mode.wireless")));
        this.modeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.interface_mode.normal")));
        addToLeftToolbar(this.modeButton);

        this.exportModeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_EXPORT, btn -> menu.cycleExportMode());
        this.exportModeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.export_mode.auto")));
        this.exportModeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.export_mode.off")));
        addToLeftToolbar(this.exportModeButton);

        this.importModeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_IMPORT, btn -> menu.cycleImportMode());
        addToLeftToolbar(this.importModeButton);

        this.speedButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.SPEED, btn -> menu.cycleIOSpeed());
        this.speedButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.io_speed.fast")));
        this.speedButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.io_speed.normal")));
        addToLeftToolbar(this.speedButton);

        widgets.addOpenPriorityButton();

        this.configSlots = menu.getAllConfigSlots();
        for (int i = 0; i < configSlots.size(); i++) {
            final int slotIdx = i;
            var button = new SetAmountButton(btn -> {
                if (hasShiftDown()) {
                    menu.toggleUnlimited(configSlots.get(slotIdx).getContainerSlot());
                } else {
                    menu.openSetAmountMenu(configSlots.get(slotIdx).getContainerSlot());
                }
            });
            button.setDisableBackground(true);
            button.setMessage(ButtonToolTips.InterfaceSetStockAmount.text());
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("ae2lt.gui.set_amount.tooltip")));
            amountButtons.add(button);
        }
    }

    @Override
    protected void init() {
        super.init();

        for (var btn : amountButtons) {
            addRenderableWidget(btn);
        }

        prevPageBtn = Button.builder(Component.literal("<"), b -> menu.prevPage())
                .pos(this.leftPos + 60, this.topPos + 4)
                .size(12, 12)
                .build();
        nextPageBtn = Button.builder(Component.literal(">"), b -> menu.nextPage())
                .pos(this.leftPos + 104, this.topPos + 4)
                .size(12, 12)
                .build();
        addRenderableWidget(prevPageBtn);
        addRenderableWidget(nextPageBtn);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.fuzzyMode.set(menu.getFuzzyMode());
        this.fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));

        this.modeButton.setState(menu.interfaceMode == 1);
        this.exportModeButton.setState(menu.exportMode == OverloadedInterfaceBlockEntity.ExportMode.AUTO.ordinal());
        this.speedButton.setState(menu.ioSpeedMode == 1);

        var impMode = OverloadedInterfaceBlockEntity.ImportMode.values()[
                Math.min(menu.importMode, OverloadedInterfaceBlockEntity.ImportMode.values().length - 1)];
        switch (impMode) {
            case OFF -> {
                importModeButton.setState(false);
                importModeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.import_mode.off")));
                importModeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.import_mode.off")));
            }
            case AUTO -> {
                importModeButton.setState(true);
                importModeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.import_mode.auto")));
                importModeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.import_mode.auto")));
            }
            case EJECT -> {
                importModeButton.setState(true);
                importModeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.import_mode.eject")));
                importModeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.import_mode.eject")));
            }
        }

        if (menu.currentPage != lastKnownPage) {
            lastKnownPage = menu.currentPage;
            menu.showPage(menu.currentPage);
        }

        int page = menu.currentPage;
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, configSlots.size());

        for (int i = 0; i < amountButtons.size(); i++) {
            var button = amountButtons.get(i);
            if (i >= start && i < end) {
                int inPage = i - start;
                int col = inPage % COLS;
                int row = inPage / COLS;
                button.setPosition(
                        this.leftPos + AMT_START_X + col * SLOT_SPACING,
                        this.topPos + (row == 0 ? AMT_ROW1_Y : AMT_ROW2_Y));
                var item = configSlots.get(i).getItem();
                button.visible = !item.isEmpty();
            } else {
                button.visible = false;
            }
        }

        if (prevPageBtn != null) prevPageBtn.active = page > 0;
        if (nextPageBtn != null) nextPageBtn.active = page < menu.totalPages - 1;
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY,
                        int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        String pageText = (menu.currentPage + 1) + "/" + menu.totalPages;
        int textWidth = this.font.width(pageText);
        guiGraphics.drawString(this.font, pageText, (176 - textWidth) / 2, 7,
                style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);

        int page = menu.currentPage;
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, configSlots.size());
        for (int i = start; i < end; i++) {
            if (menu.isSlotUnlimited(i)) {
                var slot = configSlots.get(i);
                if (!slot.getItem().isEmpty()) {
                    guiGraphics.drawString(this.font, "\u221E",
                            slot.x + 10, slot.y - 10, 0xFF00FF00, true);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    static class SetAmountButton extends appeng.client.gui.widgets.IconButton {
        public SetAmountButton(OnPress onPress) {
            super(onPress);
        }

        @Override
        protected Icon getIcon() {
            return isHoveredOrFocused() ? Icon.COG : Icon.COG_DISABLED;
        }
    }
}
