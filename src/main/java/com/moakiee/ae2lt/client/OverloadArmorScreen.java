package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import com.moakiee.ae2lt.menu.OverloadArmorMenu;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleOptionUi;

public class OverloadArmorScreen extends AbstractContainerScreen<OverloadArmorMenu> {
    private static final Component SCREEN_TITLE = Component.translatable("item.ae2lt.overload_armor");
    private static final Component OPEN_TERMINAL = Component.translatable("ae2lt.overload_armor.open_terminal");
    private static final Component MODULE_BAY = Component.translatable("ae2lt.overload_armor.screen.module_bay");
    private static final Component MODULE_CONTROL = Component.translatable("ae2lt.overload_armor.screen.module_control");
    private static final Component MODULE_OPTIONS = Component.translatable("ae2lt.overload_armor.screen.module_options");
    private static final Component NO_FEATURES = Component.translatable("ae2lt.overload_armor.screen.no_features");
    private static final Component NO_CONFIG = Component.translatable("ae2lt.overload_armor.screen.action.no_config_hint");
    private static final Component MODULE_LIST_HINT =
            Component.translatable("ae2lt.overload_armor.screen.module_cycle_hint");
    private static final Component TOGGLE_ENABLE_HINT =
            Component.translatable("ae2lt.overload_armor.screen.action.click_toggle");

    // Panel geometry — kept identical to the previous screen so the 332x162 background still fits.
    private static final int LEFT_PANEL_X = 7;
    private static final int CENTER_PANEL_X = 121;
    private static final int RIGHT_PANEL_X = 213;
    private static final int PANEL_TOP = 16;
    private static final int PANEL_BOTTOM_PADDING = 7;
    private static final int LEFT_PANEL_WIDTH = 110;
    private static final int CENTER_PANEL_WIDTH = 88;
    private static final int RIGHT_PANEL_WIDTH = 112;

    // Submodule list on center panel.
    private static final int SUBMODULE_LIST_X = CENTER_PANEL_X + 6;
    private static final int SUBMODULE_LIST_Y = 22;
    private static final int SUBMODULE_LIST_WIDTH = CENTER_PANEL_WIDTH - 14;
    private static final int SUBMODULE_LIST_ROW_HEIGHT = 20;
    private static final int SUBMODULE_VISIBLE_ROWS = 5;
    private static final int SUBMODULE_TOGGLE_SIZE = 10;
    private static final int SUBMODULE_TOGGLE_INSET = 4;

    // Config list on right panel.
    private static final int CONFIG_LIST_X = RIGHT_PANEL_X + 6;
    private static final int CONFIG_LIST_Y = 82;
    private static final int CONFIG_LIST_WIDTH = RIGHT_PANEL_WIDTH - 12;
    private static final int CONFIG_LIST_ROW_HEIGHT = 14;
    private static final int CONFIG_VISIBLE_ROWS = 4;
    private static final int CONFIG_CONTROL_WIDTH = 52;
    private static final int CONFIG_ARROW_WIDTH = 10;

    private static final int COLOR_PANEL_BG = 0xFF232323;
    private static final int COLOR_LEFT_PANEL_BG = 0xFF1F1F1F;
    private static final int COLOR_ROW_BG = 0xFF181818;
    private static final int COLOR_ROW_BG_INNER = 0xFF202020;
    private static final int COLOR_ROW_SELECTED = 0xFF4A4A4A;
    private static final int COLOR_ROW_SELECTED_INNER = 0xFF5A5A5A;
    private static final int COLOR_TOGGLE_ON = 0xFFF6D365;
    private static final int COLOR_TOGGLE_OFF = 0xFF5D6270;
    private static final int COLOR_CONTROL_BG = 0xFF141414;
    private static final int COLOR_CONTROL_BG_HOVER = 0xFF2C2C2C;
    private static final int COLOR_ARROW_ENABLED = 0xFFE0E0E0;
    private static final int COLOR_ARROW_DISABLED = 0xFF5A5A5A;

    private Button openTerminalButton;
    private int selectedSubmoduleIndex = -1;
    private int submoduleScrollOffset;
    private int selectedConfigIndex = -1;
    private int configScrollOffset;

    public OverloadArmorScreen(OverloadArmorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 332;
        this.imageHeight = 162;
    }

    @Override
    protected void init() {
        super.init();
        syncSelectedSubmodule();

        openTerminalButton = addRenderableWidget(Button.builder(OPEN_TERMINAL, button -> menu.clientOpenTerminal())
                .bounds(leftPos + 14, topPos + 20, 96, 20)
                .build());
        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        menu.syncClientSubmoduleStateFromServer();
        syncSelectedSubmodule();
        updateButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && isInsideSubmoduleList(mouseX, mouseY)) {
            int count = menu.getSubmoduleCount();
            if (count > SUBMODULE_VISIBLE_ROWS) {
                submoduleScrollOffset = Mth.clamp(
                        submoduleScrollOffset - (int) Math.signum(scrollY),
                        0,
                        Math.max(0, count - SUBMODULE_VISIBLE_ROWS));
            }
            return true;
        }
        if (scrollY != 0 && isInsideConfigList(mouseX, mouseY)) {
            var configUi = getSelectedConfigUi();
            if (configUi.size() > CONFIG_VISIBLE_ROWS) {
                configScrollOffset = Mth.clamp(
                        configScrollOffset - (int) Math.signum(scrollY),
                        0,
                        Math.max(0, configUi.size() - CONFIG_VISIBLE_ROWS));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && handleSubmoduleRowClick(mouseX, mouseY, button)) {
            return true;
        }
        if ((button == 0 || button == 1) && handleConfigRowClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF1E1E1E);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF313131);
        graphics.fill(
                leftPos + LEFT_PANEL_X,
                topPos + PANEL_TOP,
                leftPos + LEFT_PANEL_X + LEFT_PANEL_WIDTH,
                topPos + imageHeight - PANEL_BOTTOM_PADDING,
                COLOR_LEFT_PANEL_BG);
        graphics.fill(
                leftPos + CENTER_PANEL_X,
                topPos + PANEL_TOP,
                leftPos + CENTER_PANEL_X + CENTER_PANEL_WIDTH,
                topPos + imageHeight - PANEL_BOTTOM_PADDING,
                COLOR_PANEL_BG);
        graphics.fill(
                leftPos + RIGHT_PANEL_X,
                topPos + PANEL_TOP,
                leftPos + RIGHT_PANEL_X + RIGHT_PANEL_WIDTH,
                topPos + imageHeight - PANEL_BOTTOM_PADDING,
                COLOR_PANEL_BG);
        renderSubmoduleList(graphics, mouseX, mouseY);
        renderConfigList(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, SCREEN_TITLE, 8, 6, 0xE0E0E0, false);
        graphics.drawString(font,
                Component.translatable("ae2lt.overload_armor.screen.load", menu.currentLoad, menu.baseOverload),
                8, 49, 0xE0E0E0, false);
        graphics.drawString(font,
                Component.translatable("ae2lt.overload_armor.screen.energy", menu.storedEnergy, menu.bufferCapacity),
                8, 61, 0xE0E0E0, false);

        Component detail = menu.lockedTicks > 0
                ? Component.translatable("ae2lt.overload_armor.screen.locked", menu.lockedTicks / 20)
                : menu.unpaidEnergy > 0
                        ? Component.translatable("ae2lt.overload_armor.screen.debt", menu.unpaidEnergy, menu.debtTicks, 10)
                        : menu.getStatusText();
        graphics.drawString(font, detail, 8, 73, 0xF6D365, false);
        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.equipped",
                        Component.translatable(menu.isEquipped()
                                ? "ae2lt.overload_armor.screen.flag.yes"
                                : "ae2lt.overload_armor.screen.flag.no")),
                8, 85, 0xB8B8B8, false);
        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.core",
                        Component.translatable(menu.hasCoreInstalled()
                                ? "ae2lt.overload_armor.screen.flag.installed"
                                : "ae2lt.overload_armor.screen.flag.missing")),
                8, 97, 0xB8B8B8, false);
        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.buffer",
                        Component.translatable(menu.hasBufferInstalled()
                                ? "ae2lt.overload_armor.screen.flag.installed"
                                : "ae2lt.overload_armor.screen.flag.missing")),
                8, 109, 0xB8B8B8, false);
        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.terminal",
                        Component.translatable(menu.hasTerminalInstalled()
                                ? "ae2lt.overload_armor.screen.flag.installed"
                                : "ae2lt.overload_armor.screen.flag.missing")),
                8, 121, 0xB8B8B8, false);
        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.features_count",
                        menu.getEnabledFeatureCount(),
                        menu.getSubmoduleCount()),
                8, 133, 0x8A8A8A, false);

        graphics.drawString(font, MODULE_BAY, 129, 6, 0xE0E0E0, false);
        graphics.drawString(font, MODULE_CONTROL, 221, 6, 0xE0E0E0, false);

        int selectedIndex = getSelectedSubmoduleIndex();
        if (selectedIndex < 0) {
            graphics.drawCenteredString(font, NO_FEATURES, CENTER_PANEL_X + CENTER_PANEL_WIDTH / 2, 119, 0x8A8A8A);
            drawWrappedText(graphics, Component.translatable("ae2lt.overload_armor.screen.hint"),
                    RIGHT_PANEL_X + 8, 22, RIGHT_PANEL_WIDTH - 16, 0x8A8A8A, 6);
            return;
        }

        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.module_index",
                        selectedIndex + 1, menu.getSubmoduleCount()),
                129, 128, 0xB8B8B8, false);
        drawWrappedText(graphics, MODULE_LIST_HINT, 129, 140, CENTER_PANEL_WIDTH - 16, 0x8A8A8A, 2);

        drawCenteredSplitText(
                graphics, menu.getSubmoduleName(selectedIndex),
                RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2, 20, RIGHT_PANEL_WIDTH - 16, 0xE0E0E0, 2);
        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.module_status",
                        menu.getSubmoduleStatusText(selectedIndex)),
                RIGHT_PANEL_X + 8, 42, 0xB8B8B8, false);
        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.module_enabled",
                        Component.translatable(menu.isSubmoduleEnabled(selectedIndex)
                                ? "ae2lt.overload_armor.screen.flag.yes"
                                : "ae2lt.overload_armor.screen.flag.no")),
                RIGHT_PANEL_X + 8, 54, 0xB8B8B8, false);
        graphics.drawString(font,
                Component.translatable(
                        "ae2lt.overload_armor.screen.module_load_pair",
                        menu.getSubmoduleIdleOverloaded(selectedIndex),
                        menu.getSubmoduleDynamicOverloaded(selectedIndex)),
                RIGHT_PANEL_X + 8, 66, 0xB8B8B8, false);
        graphics.drawString(font, MODULE_OPTIONS, RIGHT_PANEL_X + 8, 76, 0x8A8A8A, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoveredSubmoduleTooltip(graphics, mouseX, mouseY);
        renderHoveredConfigTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void updateButtons() {
        if (openTerminalButton != null) {
            openTerminalButton.active = menu.terminalReady != 0;
        }
    }

    private void renderSubmoduleList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listLeft = leftPos + SUBMODULE_LIST_X;
        int listTop = topPos + SUBMODULE_LIST_Y;
        int listRight = listLeft + SUBMODULE_LIST_WIDTH;
        int scrollbarLeft = leftPos + CENTER_PANEL_X + CENTER_PANEL_WIDTH - 6;
        int count = menu.getSubmoduleCount();

        for (int row = 0; row < SUBMODULE_VISIBLE_ROWS; row++) {
            int submoduleIndex = submoduleScrollOffset + row;
            int rowTop = listTop + row * SUBMODULE_LIST_ROW_HEIGHT;
            int rowBottom = rowTop + SUBMODULE_LIST_ROW_HEIGHT - 2;
            boolean selected = submoduleIndex == getSelectedSubmoduleIndex();
            graphics.fill(listLeft, rowTop, listRight, rowBottom, selected ? COLOR_ROW_SELECTED : COLOR_ROW_BG);
            graphics.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom - 1,
                    selected ? COLOR_ROW_SELECTED_INNER : COLOR_ROW_BG_INNER);

            if (submoduleIndex >= count) {
                continue;
            }

            // Clickable toggle square on the left of the row: click flips enabled state without
            // having to open a secondary menu. Border brightens when the cursor hovers over it.
            int toggleLeft = listLeft + SUBMODULE_TOGGLE_INSET;
            int toggleTop = rowTop + (SUBMODULE_LIST_ROW_HEIGHT - 2 - SUBMODULE_TOGGLE_SIZE) / 2;
            boolean enabled = menu.isSubmoduleEnabled(submoduleIndex);
            boolean toggleHovered = isInsideRect(mouseX, mouseY,
                    toggleLeft, toggleTop, SUBMODULE_TOGGLE_SIZE, SUBMODULE_TOGGLE_SIZE);
            graphics.fill(toggleLeft, toggleTop, toggleLeft + SUBMODULE_TOGGLE_SIZE, toggleTop + SUBMODULE_TOGGLE_SIZE,
                    toggleHovered ? 0xFFEFEFEF : 0xFF101010);
            graphics.fill(toggleLeft + 1, toggleTop + 1, toggleLeft + SUBMODULE_TOGGLE_SIZE - 1, toggleTop + SUBMODULE_TOGGLE_SIZE - 1,
                    enabled ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF);

            int nameLeft = toggleLeft + SUBMODULE_TOGGLE_SIZE + 4;
            int rowTextY = rowTop + (SUBMODULE_LIST_ROW_HEIGHT - 2 - 8) / 2;

            // Right-flush badge: "×N" normally, "×N/Max" when the submodule declares a cap. The
            // badge's width is reserved from the name's drawable region so it never overlaps.
            int amount = menu.getSubmoduleInstalledAmount(submoduleIndex);
            int cap = menu.getSubmoduleMaxInstallAmount(submoduleIndex);
            int nameRightBound = listRight - 2;
            if (amount > 1 || cap > 0) {
                String badge = cap > 0 ? "×" + amount + "/" + cap : "×" + amount;
                int badgeWidth = font.width(badge);
                int badgeX = listRight - badgeWidth - 3;
                graphics.drawString(font, badge, badgeX, rowTextY, 0xFFF6D365, false);
                nameRightBound = badgeX - 2;
            }

            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(menu.getSubmoduleName(submoduleIndex).getString(),
                            Math.max(0, nameRightBound - nameLeft)),
                    nameLeft,
                    rowTextY,
                    selected ? 0xFFF3F3F3 : 0xFFE0E0E0,
                    false);
        }

        if (count > SUBMODULE_VISIBLE_ROWS) {
            int trackTop = listTop;
            int trackBottom = listTop + SUBMODULE_VISIBLE_ROWS * SUBMODULE_LIST_ROW_HEIGHT - 2;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(14, trackHeight * SUBMODULE_VISIBLE_ROWS / count);
            int maxScroll = Math.max(1, count - SUBMODULE_VISIBLE_ROWS);
            int thumbOffset = (trackHeight - thumbHeight) * submoduleScrollOffset / maxScroll;
            graphics.fill(scrollbarLeft, trackTop, scrollbarLeft + 3, trackBottom, 0xFF161616);
            graphics.fill(scrollbarLeft, trackTop + thumbOffset, scrollbarLeft + 3,
                    trackTop + thumbOffset + thumbHeight, 0xFF6A6A6A);
        }
    }

    private void renderConfigList(GuiGraphics graphics, int mouseX, int mouseY) {
        int selectedIndex = getSelectedSubmoduleIndex();
        if (selectedIndex < 0) {
            return;
        }

        var configUi = menu.getSubmoduleConfigUi(selectedIndex);
        if (configUi.isEmpty()) {
            drawWrappedText(graphics, NO_CONFIG, leftPos + CONFIG_LIST_X,
                    topPos + CONFIG_LIST_Y + 4, CONFIG_LIST_WIDTH, 0x8A8A8A, 3);
            return;
        }

        int listLeft = leftPos + CONFIG_LIST_X;
        int listTop = topPos + CONFIG_LIST_Y;
        int listRight = listLeft + CONFIG_LIST_WIDTH;
        int scrollbarLeft = leftPos + RIGHT_PANEL_X + RIGHT_PANEL_WIDTH - 6;

        for (int row = 0; row < CONFIG_VISIBLE_ROWS; row++) {
            int configIndex = configScrollOffset + row;
            int rowTop = listTop + row * CONFIG_LIST_ROW_HEIGHT;
            int rowBottom = rowTop + CONFIG_LIST_ROW_HEIGHT - 2;
            boolean selected = configIndex == getSelectedConfigIndex();
            graphics.fill(listLeft, rowTop, listRight, rowBottom, selected ? COLOR_ROW_SELECTED : COLOR_ROW_BG);
            graphics.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom - 1,
                    selected ? COLOR_ROW_SELECTED_INNER : COLOR_ROW_BG_INNER);

            if (configIndex >= configUi.size()) {
                continue;
            }

            var option = configUi.get(configIndex);

            int controlRight = listRight - 2;
            int controlLeft = controlRight - CONFIG_CONTROL_WIDTH;
            int controlTop = rowTop + 2;
            int controlBottom = rowBottom - 2;

            // Left side: option label, truncated to fit. Right side: inline control widget (pill
            // or arrows) so the user can click directly without selecting first.
            int labelMaxWidth = controlLeft - (listLeft + 4) - 2;
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(option.label().getString(), labelMaxWidth),
                    listLeft + 4,
                    rowTop + (CONFIG_LIST_ROW_HEIGHT - 2 - 8) / 2,
                    selected ? 0xFFF3F3F3 : 0xFFE0E0E0,
                    false);

            renderConfigControl(graphics, option, controlLeft, controlTop, controlRight, controlBottom, mouseX, mouseY);
        }

        if (configUi.size() > CONFIG_VISIBLE_ROWS) {
            int trackTop = listTop;
            int trackBottom = listTop + CONFIG_VISIBLE_ROWS * CONFIG_LIST_ROW_HEIGHT - 2;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(10, trackHeight * CONFIG_VISIBLE_ROWS / configUi.size());
            int maxScroll = Math.max(1, configUi.size() - CONFIG_VISIBLE_ROWS);
            int thumbOffset = (trackHeight - thumbHeight) * configScrollOffset / maxScroll;
            graphics.fill(scrollbarLeft, trackTop, scrollbarLeft + 3, trackBottom, 0xFF161616);
            graphics.fill(scrollbarLeft, trackTop + thumbOffset, scrollbarLeft + 3,
                    trackTop + thumbOffset + thumbHeight, 0xFF6A6A6A);
        }
    }

    private void renderConfigControl(
            GuiGraphics graphics,
            OverloadArmorSubmoduleOptionUi option,
            int left,
            int top,
            int right,
            int bottom,
            int mouseX,
            int mouseY
    ) {
        int width = right - left;
        int height = bottom - top;
        boolean hovered = isInsideRect(mouseX, mouseY, left, top, width, height);
        switch (option.kind()) {
            case BOOLEAN -> {
                boolean on = isBooleanValueOn(option);
                int pillColor = on ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
                graphics.fill(left, top, right, bottom, hovered ? COLOR_CONTROL_BG_HOVER : COLOR_CONTROL_BG);
                graphics.fill(left + 1, top + 1, right - 1, bottom - 1, pillColor);
                String label = font.plainSubstrByWidth(option.value().getString(), Math.max(0, width - 4));
                if (!label.isEmpty()) {
                    graphics.drawString(font, label,
                            left + width / 2 - font.width(label) / 2,
                            top + (height - 8) / 2,
                            0xFF101010, false);
                }
            }
            case CYCLE -> {
                graphics.fill(left, top, right, bottom, hovered ? COLOR_CONTROL_BG_HOVER : COLOR_CONTROL_BG);
                // Arrow buttons at either end; the user clicks an arrow to step in that direction.
                int leftArrowRight = left + CONFIG_ARROW_WIDTH;
                int rightArrowLeft = right - CONFIG_ARROW_WIDTH;
                boolean leftHover = isInsideRect(mouseX, mouseY, left, top, CONFIG_ARROW_WIDTH, height);
                boolean rightHover = isInsideRect(mouseX, mouseY, rightArrowLeft, top, CONFIG_ARROW_WIDTH, height);
                if (leftHover) {
                    graphics.fill(left, top, leftArrowRight, bottom, 0xFF3A3A3A);
                }
                if (rightHover) {
                    graphics.fill(rightArrowLeft, top, right, bottom, 0xFF3A3A3A);
                }
                int arrowY = top + (height - 8) / 2;
                graphics.drawString(font, "<", left + 3, arrowY, COLOR_ARROW_ENABLED, false);
                graphics.drawString(font, ">", right - 7, arrowY, COLOR_ARROW_ENABLED, false);
                int textLeft = leftArrowRight + 1;
                int textRight = rightArrowLeft - 1;
                int textWidth = Math.max(0, textRight - textLeft);
                // plainSubstrByWidth truncates by pixel width; font.split would word-wrap and cause
                // labels sharing a common prefix ("Mode Alpha"/"Mode Beta"/"Mode Gamma") to collapse
                // to just "Mode", making the cycle look stuck on a single option.
                String label = font.plainSubstrByWidth(option.value().getString(), textWidth);
                if (!label.isEmpty()) {
                    graphics.drawString(font, label,
                            textLeft + textWidth / 2 - font.width(label) / 2,
                            arrowY, 0xFFEDEDED, false);
                }
            }
            case READ_ONLY -> {
                String label = font.plainSubstrByWidth(option.value().getString(), Math.max(0, width - 4));
                if (!label.isEmpty()) {
                    graphics.drawString(font, label,
                            left + width - font.width(label) - 2,
                            top + (height - 8) / 2,
                            COLOR_ARROW_DISABLED, false);
                }
            }
        }
    }

    private boolean handleSubmoduleRowClick(double mouseX, double mouseY, int button) {
        int hoveredIndex = getHoveredSubmoduleIndex(mouseX, mouseY);
        if (hoveredIndex < 0) {
            return false;
        }

        // Clicking the left toggle square flips enabled state directly; clicking the rest of the
        // row only selects (so the right panel can update its detail view).
        if (button == 0 && isOverSubmoduleToggle(mouseX, mouseY, hoveredIndex)) {
            menu.clientToggleFeature(hoveredIndex);
            selectSubmodule(hoveredIndex);
            return true;
        }

        selectSubmodule(hoveredIndex);
        return true;
    }

    private boolean handleConfigRowClick(double mouseX, double mouseY, int button) {
        int selectedSubmodule = getSelectedSubmoduleIndex();
        if (selectedSubmodule < 0) {
            return false;
        }

        int hoveredIndex = getHoveredConfigIndex(mouseX, mouseY);
        if (hoveredIndex < 0) {
            return false;
        }

        var configUi = menu.getSubmoduleConfigUi(selectedSubmodule);
        if (hoveredIndex >= configUi.size()) {
            return false;
        }

        selectConfig(hoveredIndex);
        var option = configUi.get(hoveredIndex);
        if (!option.editable()) {
            return true;
        }

        ConfigControlArea area = getConfigControlArea(hoveredIndex);
        ConfigHit hit = locateConfigHit(mouseX, mouseY, area, option);
        if (hit == ConfigHit.OUTSIDE_CONTROL) {
            return true;
        }

        boolean forward = switch (hit) {
            case ARROW_LEFT -> false;
            case ARROW_RIGHT -> true;
            // Full-width BOOLEAN pill or fallthrough in CYCLE: left click = forward, right click = backward.
            default -> button != 1;
        };
        menu.clientCycleSubmoduleConfig(selectedSubmodule, hoveredIndex, forward);
        return true;
    }

    private boolean isOverSubmoduleToggle(double mouseX, double mouseY, int submoduleIndex) {
        int row = submoduleIndex - submoduleScrollOffset;
        if (row < 0 || row >= SUBMODULE_VISIBLE_ROWS) {
            return false;
        }
        int listLeft = leftPos + SUBMODULE_LIST_X;
        int listTop = topPos + SUBMODULE_LIST_Y;
        int toggleLeft = listLeft + SUBMODULE_TOGGLE_INSET;
        int toggleTop = listTop + row * SUBMODULE_LIST_ROW_HEIGHT
                + (SUBMODULE_LIST_ROW_HEIGHT - 2 - SUBMODULE_TOGGLE_SIZE) / 2;
        return isInsideRect(mouseX, mouseY, toggleLeft, toggleTop, SUBMODULE_TOGGLE_SIZE, SUBMODULE_TOGGLE_SIZE);
    }

    private ConfigControlArea getConfigControlArea(int configIndex) {
        int row = configIndex - configScrollOffset;
        int listLeft = leftPos + CONFIG_LIST_X;
        int listTop = topPos + CONFIG_LIST_Y;
        int listRight = listLeft + CONFIG_LIST_WIDTH;
        int rowTop = listTop + row * CONFIG_LIST_ROW_HEIGHT;
        int rowBottom = rowTop + CONFIG_LIST_ROW_HEIGHT - 2;
        int controlRight = listRight - 2;
        int controlLeft = controlRight - CONFIG_CONTROL_WIDTH;
        return new ConfigControlArea(controlLeft, rowTop + 2, controlRight, rowBottom - 2);
    }

    private ConfigHit locateConfigHit(double mouseX, double mouseY, ConfigControlArea area, OverloadArmorSubmoduleOptionUi option) {
        if (!isInsideRect(mouseX, mouseY, area.left(), area.top(), area.right() - area.left(), area.bottom() - area.top())) {
            return ConfigHit.OUTSIDE_CONTROL;
        }
        if (option.kind() == OverloadArmorSubmoduleOptionUi.Kind.CYCLE) {
            if (mouseX < area.left() + CONFIG_ARROW_WIDTH) {
                return ConfigHit.ARROW_LEFT;
            }
            if (mouseX >= area.right() - CONFIG_ARROW_WIDTH) {
                return ConfigHit.ARROW_RIGHT;
            }
        }
        return ConfigHit.CENTER;
    }

    private void renderHoveredSubmoduleTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int hoveredIndex = getHoveredSubmoduleIndex(mouseX, mouseY);
        if (hoveredIndex < 0) {
            return;
        }
        Component tooltip = isOverSubmoduleToggle(mouseX, mouseY, hoveredIndex)
                ? TOGGLE_ENABLE_HINT
                : menu.getSubmoduleTooltipText(hoveredIndex);
        graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private void renderHoveredConfigTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int hoveredIndex = getHoveredConfigIndex(mouseX, mouseY);
        if (hoveredIndex < 0) {
            return;
        }

        var configUi = getSelectedConfigUi();
        if (hoveredIndex >= configUi.size()) {
            return;
        }

        var option = configUi.get(hoveredIndex);
        Component tooltip = option.hint() != null
                ? option.hint()
                : Component.literal(option.label().getString() + ": " + option.value().getString());
        graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private int getHoveredSubmoduleIndex(double mouseX, double mouseY) {
        if (!isInsideSubmoduleList(mouseX, mouseY)) {
            return -1;
        }

        int row = (int) ((mouseY - (topPos + SUBMODULE_LIST_Y)) / SUBMODULE_LIST_ROW_HEIGHT);
        int index = submoduleScrollOffset + row;
        return index >= 0 && index < menu.getSubmoduleCount() ? index : -1;
    }

    private int getHoveredConfigIndex(double mouseX, double mouseY) {
        if (!isInsideConfigList(mouseX, mouseY)) {
            return -1;
        }

        var configUi = getSelectedConfigUi();
        if (configUi.isEmpty()) {
            return -1;
        }

        int row = (int) ((mouseY - (topPos + CONFIG_LIST_Y)) / CONFIG_LIST_ROW_HEIGHT);
        int index = configScrollOffset + row;
        return index >= 0 && index < configUi.size() ? index : -1;
    }

    private void selectSubmodule(int index) {
        selectedSubmoduleIndex = index;
        ensureSelectedVisible();
        selectedConfigIndex = 0;
        configScrollOffset = 0;
        ensureSelectedConfigVisible();
    }

    private void selectConfig(int index) {
        selectedConfigIndex = index;
        ensureSelectedConfigVisible();
    }

    private void syncSelectedSubmodule() {
        int count = menu.getSubmoduleCount();
        if (count <= 0) {
            selectedSubmoduleIndex = -1;
            submoduleScrollOffset = 0;
            selectedConfigIndex = -1;
            configScrollOffset = 0;
            return;
        }

        boolean selectionChanged = false;
        if (selectedSubmoduleIndex < 0 || selectedSubmoduleIndex >= count) {
            selectedSubmoduleIndex = 0;
            selectionChanged = true;
        }
        submoduleScrollOffset = Mth.clamp(submoduleScrollOffset, 0, Math.max(0, count - SUBMODULE_VISIBLE_ROWS));
        // Only pull the scroll viewport to the selected row when we just reseated the selection
        // — otherwise user-initiated scrolling (wheel) gets snapped back every tick.
        if (selectionChanged) {
            ensureSelectedVisible();
        }
        syncSelectedConfig();
    }

    private void ensureSelectedVisible() {
        int count = menu.getSubmoduleCount();
        if (count <= 0 || selectedSubmoduleIndex < 0) {
            return;
        }

        if (selectedSubmoduleIndex < submoduleScrollOffset) {
            submoduleScrollOffset = selectedSubmoduleIndex;
        } else if (selectedSubmoduleIndex >= submoduleScrollOffset + SUBMODULE_VISIBLE_ROWS) {
            submoduleScrollOffset = selectedSubmoduleIndex - SUBMODULE_VISIBLE_ROWS + 1;
        }
        submoduleScrollOffset = Mth.clamp(submoduleScrollOffset, 0, Math.max(0, count - SUBMODULE_VISIBLE_ROWS));
    }

    private void syncSelectedConfig() {
        var configUi = getSelectedConfigUi();
        if (configUi.isEmpty()) {
            selectedConfigIndex = -1;
            configScrollOffset = 0;
            return;
        }

        boolean selectionChanged = false;
        if (selectedConfigIndex < 0 || selectedConfigIndex >= configUi.size()) {
            selectedConfigIndex = 0;
            selectionChanged = true;
        }
        configScrollOffset = Mth.clamp(configScrollOffset, 0, Math.max(0, configUi.size() - CONFIG_VISIBLE_ROWS));
        if (selectionChanged) {
            ensureSelectedConfigVisible();
        }
    }

    private void ensureSelectedConfigVisible() {
        var configUi = getSelectedConfigUi();
        if (configUi.isEmpty() || selectedConfigIndex < 0) {
            return;
        }

        if (selectedConfigIndex < configScrollOffset) {
            configScrollOffset = selectedConfigIndex;
        } else if (selectedConfigIndex >= configScrollOffset + CONFIG_VISIBLE_ROWS) {
            configScrollOffset = selectedConfigIndex - CONFIG_VISIBLE_ROWS + 1;
        }
        configScrollOffset = Mth.clamp(configScrollOffset, 0, Math.max(0, configUi.size() - CONFIG_VISIBLE_ROWS));
    }

    private int getSelectedSubmoduleIndex() {
        syncSelectedSubmodule();
        return selectedSubmoduleIndex;
    }

    private int getSelectedConfigIndex() {
        syncSelectedConfig();
        return selectedConfigIndex;
    }

    private List<OverloadArmorSubmoduleOptionUi> getSelectedConfigUi() {
        return selectedSubmoduleIndex >= 0
                ? menu.getSubmoduleConfigUi(selectedSubmoduleIndex)
                : List.of();
    }

    private boolean isInsideSubmoduleList(double mouseX, double mouseY) {
        int listLeft = leftPos + SUBMODULE_LIST_X;
        int listTop = topPos + SUBMODULE_LIST_Y;
        int listRight = listLeft + SUBMODULE_LIST_WIDTH;
        int listBottom = listTop + SUBMODULE_VISIBLE_ROWS * SUBMODULE_LIST_ROW_HEIGHT;
        return mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom;
    }

    private boolean isInsideConfigList(double mouseX, double mouseY) {
        int listLeft = leftPos + CONFIG_LIST_X;
        int listTop = topPos + CONFIG_LIST_Y;
        int listRight = listLeft + CONFIG_LIST_WIDTH;
        int listBottom = listTop + CONFIG_VISIBLE_ROWS * CONFIG_LIST_ROW_HEIGHT;
        return mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom;
    }

    private static boolean isInsideRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static boolean isBooleanValueOn(OverloadArmorSubmoduleOptionUi option) {
        var raw = option.value().getString();
        return raw.equalsIgnoreCase("on") || raw.equalsIgnoreCase("true")
                || raw.equalsIgnoreCase(Component.translatable("ae2lt.overload_armor.screen.flag.yes").getString());
    }

    private void drawWrappedText(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int width,
            int color,
            int maxLines
    ) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int visibleLines = Math.min(lines.size(), maxLines);
        for (int line = 0; line < visibleLines; line++) {
            graphics.drawString(font, lines.get(line), x, y + line * 10, color);
        }
    }

    private void drawCenteredSplitText(
            GuiGraphics graphics,
            Component text,
            int centerX,
            int y,
            int width,
            int color,
            int maxLines
    ) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int visibleLines = Math.min(lines.size(), maxLines);
        for (int line = 0; line < visibleLines; line++) {
            FormattedCharSequence sequence = lines.get(line);
            graphics.drawString(font, sequence, centerX - font.width(sequence) / 2, y + line * 10, color);
        }
    }

    private record ConfigControlArea(int left, int top, int right, int bottom) {
    }

    private enum ConfigHit {
        OUTSIDE_CONTROL,
        CENTER,
        ARROW_LEFT,
        ARROW_RIGHT
    }
}
