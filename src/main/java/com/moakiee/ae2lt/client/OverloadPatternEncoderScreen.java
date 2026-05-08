package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;
import com.moakiee.ae2lt.overload.model.MatchMode;

/**
 * Client-side screen for the overload pattern encoder.
 * <p>
 * Uses a textured 176x191 viewport and a scrollable panel that shows source
 * inputs/outputs with per-entry mode toggles.
 */
public class OverloadPatternEncoderScreen extends AbstractContainerScreen<OverloadPatternEncoderMenu> {
    private static final Component SCREEN_TITLE = Component.translatable("item.ae2lt.overload_pattern_encoder");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/ae2lt_pattern_encoder.png");
    private static final Identifier CHECKBOX_TEXTURE = Identifier.fromNamespaceAndPath(
            "ae2", "textures/guis/checkbox.png");

    private static final int TEXTURE_SIZE = 256;
    private static final int CHECKBOX_TEXTURE_SIZE = 64;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 191;

    private static final int PANEL_X = 43;
    private static final int PANEL_Y = 17;
    private static final int PANEL_WIDTH = 125;
    private static final int PANEL_HEIGHT = 75;
    private static final int TRACK_X = 45;
    private static final int TRACK_Y = 21;
    private static final int TRACK_HEIGHT = 69;
    private static final int SLIDER_U = 177;
    private static final int SLIDER_V = 29;
    private static final int SLIDER_WIDTH = 7;
    private static final int SLIDER_HEIGHT = 15;
    private static final int SLOT_U = 177;
    private static final int SLOT_V = 0;
    private static final int SLOT_SIZE = 18;
    private static final int ENTRY_TOP_OFFSET = 3;
    private static final int ENTRY_CONTENT_Y_OFFSET = 3;
    private static final int ENTRY_SLOT_X = 60;
    private static final int ENTRY_TEXT_X = 90;
    private static final int ENTRY_SWITCH_X = 138;
    private static final int ENTRY_ROW_HEIGHT = 22;
    private static final int ENTRY_SWITCH_WIDTH = 22;
    private static final int ENTRY_SWITCH_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 3;

    private int scrollOffset;
    private boolean draggingScrollbar;

    public OverloadPatternEncoderScreen(OverloadPatternEncoderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, GUI_WIDTH, GUI_HEIGHT);
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 96;
    }

    @Override
    protected void init() {
        super.init();
        clampScroll();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        clampScroll();
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBg(graphics, partialTick, mouseX, mouseY);
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    private void extractBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        blitTexture(graphics, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);
        renderEntries(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, SCREEN_TITLE, 8, 6, 0x404040, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            if (isWithinScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY);
                return true;
            }

            var entry = getEntryAt(mouseX, mouseY);
            if (entry != null && isWithinEntrySwitch(mouseX, mouseY, entry.row())) {
                toggleEntry(entry);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isWithinPanel(mouseX, mouseY) && maxScrollOffset() > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScrollOffset());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderEntries(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var entries = buildEntries();
        int start = Math.min(scrollOffset, Math.max(0, entries.size() - VISIBLE_ROWS));
        int end = Math.min(entries.size(), start + VISIBLE_ROWS);

        graphics.enableScissor(leftPos + PANEL_X, topPos + PANEL_Y, leftPos + PANEL_X + PANEL_WIDTH, topPos + PANEL_Y + PANEL_HEIGHT);
        for (int visibleRow = 0; visibleRow < end - start; visibleRow++) {
            var entry = entries.get(start + visibleRow);
            int rowY = topPos + PANEL_Y + ENTRY_TOP_OFFSET + visibleRow * ENTRY_ROW_HEIGHT;
            renderEntry(graphics, mouseX, mouseY, entry, visibleRow, rowY);
        }
        graphics.disableScissor();
    }

    private void renderEntry(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Entry entry, int visibleRow, int rowY) {
        int slotX = leftPos + ENTRY_SLOT_X;
        int textX = leftPos + ENTRY_TEXT_X;
        int switchX = leftPos + ENTRY_SWITCH_X;
        int contentY = rowY + ENTRY_CONTENT_Y_OFFSET;

        blitTexture(graphics, TEXTURE, slotX, contentY, SLOT_U, SLOT_V, SLOT_SIZE, SLOT_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.item(entry.stack(), slotX + 1, contentY + 1);
        graphics.itemDecorations(font, entry.stack(), slotX + 1, contentY + 1);

        graphics.text(font, entryLabel(entry), textX, contentY + 5, 0x404040, false);

        renderModeSwitch(graphics, switchX, contentY + 3, entry.mode());
    }

    private void renderModeSwitch(GuiGraphicsExtractor graphics, int x, int y, MatchMode mode) {
        int v = mode.ignoresComponents() ? 40 : 28;
        blitTexture(graphics, CHECKBOX_TEXTURE, x, y, 0, v, ENTRY_SWITCH_WIDTH, ENTRY_SWITCH_HEIGHT, CHECKBOX_TEXTURE_SIZE, CHECKBOX_TEXTURE_SIZE);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics) {
        if (maxScrollOffset() <= 0) {
            blitTexture(graphics, TEXTURE, leftPos + TRACK_X - 1, topPos + TRACK_Y, SLIDER_U, SLIDER_V, SLIDER_WIDTH, SLIDER_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
            return;
        }

        int sliderTravel = TRACK_HEIGHT - SLIDER_HEIGHT;
        int sliderY = topPos + TRACK_Y + Math.round((scrollOffset / (float) maxScrollOffset()) * sliderTravel);
        blitTexture(graphics, TEXTURE, leftPos + TRACK_X - 1, sliderY, SLIDER_U, SLIDER_V, SLIDER_WIDTH, SLIDER_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var entry = getEntryAt(mouseX, mouseY);
        if (entry == null) {
            super.extractTooltip(graphics, mouseX, mouseY);
            return;
        }

        int rowY = topPos + PANEL_Y + ENTRY_TOP_OFFSET + entry.row() * ENTRY_ROW_HEIGHT + ENTRY_CONTENT_Y_OFFSET;
        int slotX = leftPos + ENTRY_SLOT_X;
        if (isWithin(mouseX, mouseY, slotX, rowY, SLOT_SIZE, SLOT_SIZE)) {
            graphics.setTooltipForNextFrame(font, entry.stack(), mouseX, mouseY);
            return;
        }

        if (isWithinEntrySwitch(mouseX, mouseY, entry.row())) {
            graphics.setComponentTooltipForNextFrame(font, modeTooltip(entry), mouseX, mouseY);
            return;
        }

        super.extractTooltip(graphics, mouseX, mouseY);
    }

    private static void blitTexture(GuiGraphicsExtractor graphics, Identifier texture,
            int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight) {
        Blitter.texture(texture, textureWidth, textureHeight)
                .src(u, v, width, height)
                .dest(x, y, width, height)
                .blit(graphics);
    }

    private List<Component> modeTooltip(Entry entry) {
        var lines = new ArrayList<Component>();
        lines.add(entry.mode() == MatchMode.ID_ONLY
                ? Component.translatable("ae2lt.gui.overload_pattern_encoder.mode.id_only")
                : Component.translatable("ae2lt.gui.overload_pattern_encoder.mode.strict"));
        return lines;
    }

    @Nullable
    private Entry getEntryAt(double mouseX, double mouseY) {
        if (!isWithinPanel(mouseX, mouseY)) {
            return null;
        }

        int relativeRow = (int) ((mouseY - (topPos + PANEL_Y + ENTRY_TOP_OFFSET)) / ENTRY_ROW_HEIGHT);
        if (relativeRow < 0 || relativeRow >= VISIBLE_ROWS) {
            return null;
        }

        var entries = buildEntries();
        int index = scrollOffset + relativeRow;
        if (index < 0 || index >= entries.size()) {
            return null;
        }

        return entries.get(index).withRow(relativeRow);
    }

    private List<Entry> buildEntries() {
        var entries = new ArrayList<Entry>();
        var state = menu.syncedState;

        for (int i = 0; i < state.inputSlots().size(); i++) {
            var configured = state.inputSlots().get(i);
            entries.add(new Entry(true, configured.slotIndex(), configured.matchMode(), false, menu.getInputPreviewStack(i), -1));
        }

        for (int i = 0; i < state.outputSlots().size(); i++) {
            var configured = state.outputSlots().get(i);
            entries.add(new Entry(false, configured.slotIndex(), configured.matchMode(), false, menu.getOutputPreviewStack(i), -1));
        }

        return entries;
    }

    private void toggleEntry(Entry entry) {
        if (entry.input()) {
            menu.clientToggleInputMode(entry.slotIndex());
        } else {
            menu.clientToggleOutputMode(entry.slotIndex());
        }
    }

    private void updateScrollFromMouse(double mouseY) {
        if (maxScrollOffset() <= 0) {
            scrollOffset = 0;
            return;
        }

        float progress = (float) ((mouseY - (topPos + TRACK_Y) - SLIDER_HEIGHT / 2.0) / (TRACK_HEIGHT - SLIDER_HEIGHT));
        progress = Mth.clamp(progress, 0.0f, 1.0f);
        scrollOffset = Math.round(progress * maxScrollOffset());
    }

    private void clampScroll() {
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScrollOffset());
    }

    private int maxScrollOffset() {
        return Math.max(0, buildEntries().size() - VISIBLE_ROWS);
    }

    private boolean isWithinPanel(double mouseX, double mouseY) {
        return isWithin(mouseX, mouseY, leftPos + PANEL_X, topPos + PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
    }

    private boolean isWithinScrollbar(double mouseX, double mouseY) {
        return isWithin(mouseX, mouseY, leftPos + TRACK_X - 1, topPos + TRACK_Y, SLIDER_WIDTH, TRACK_HEIGHT);
    }

    private boolean isWithinEntrySwitch(double mouseX, double mouseY, int row) {
        int y = topPos + PANEL_Y + ENTRY_TOP_OFFSET + row * ENTRY_ROW_HEIGHT + ENTRY_CONTENT_Y_OFFSET + 3;
        return isWithin(mouseX, mouseY, leftPos + ENTRY_SWITCH_X, y, ENTRY_SWITCH_WIDTH, ENTRY_SWITCH_HEIGHT);
    }

    private static boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private Component entryLabel(Entry entry) {
        return entry.input()
                ? Component.translatable("ae2lt.gui.overload_pattern_encoder.entry.input")
                : Component.translatable("ae2lt.gui.overload_pattern_encoder.entry.output");
    }

    private record Entry(
            boolean input,
            int slotIndex,
            MatchMode mode,
            boolean primaryOutput,
            ItemStack stack,
            int row
    ) {
        private Entry withRow(int newRow) {
            return new Entry(input, slotIndex, mode, primaryOutput, stack, newRow);
        }
    }
}
