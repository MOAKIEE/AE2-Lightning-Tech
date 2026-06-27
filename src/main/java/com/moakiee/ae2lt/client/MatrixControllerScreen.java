package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class MatrixControllerScreen extends AbstractContainerScreen<MatrixControllerMenu> {
    public MatrixControllerScreen(MatrixControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 236;
        imageHeight = 168;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 12;
        int y = topPos + 136;
        addRenderableWidget(actionButton(x, y, 102, "ae2lt.matrix.gui.build", MatrixControllerActionPacket.Action.AUTO_BUILD));
        addRenderableWidget(actionButton(x + 110, y, 102, "ae2lt.matrix.gui.upgrade", MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20242A);
        guiGraphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF2D333A);
        guiGraphics.fill(leftPos + 8, topPos + 26, leftPos + imageWidth - 8, topPos + 62, 0xFF171A1F);
        guiGraphics.fill(leftPos + 8, topPos + 68, leftPos + imageWidth - 8, topPos + 128, 0xFF242A31);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 10, 0xE6EEF5, false);
        int y = 32;
        drawLine(guiGraphics, Component.translatable(
                menu.isFormed() ? "ae2lt.matrix.gui.status_formed" : "ae2lt.matrix.gui.status_unformed",
                menu.getMemberCount(),
                menu.getCraftingUnitCount()), 12, y, menu.isFormed() ? 0x85F29E : 0xF2D37A);

        y += 14;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.storage",
                menu.getPatternStorageCount(), menu.getPatternSlotCount()), 12, y, 0xB7C5D3);

        y = 74;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.mode", modeName()), 12, y, 0xE6EEF5);
        y += 12;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.dispatch_base",
                fixed(menu.getDispatchBase())), 12, y, 0xB7C5D3);
        y += 12;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.batch",
                fixed(menu.getBaseBatch()), fixed(menu.getBatchSize())), 12, y, 0xB7C5D3);
        y += 12;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.dispatches",
                fixed(menu.getDispatches()), menu.getOperationsPerTick()), 12, y, 0xB7C5D3);
        y += 12;
        drawLine(guiGraphics, Component.translatable("ae2lt.matrix.gui.heat",
                percent(menu.getNormalizedHeat()), fixed(menu.getEfficiencyFactor())), 12, y, 0xB7C5D3);
        y += 12;
        drawLine(guiGraphics, Component.translatable(statusHintKey()), 12, y, hintColor());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private Button actionButton(int x, int y, int width, String key, MatrixControllerActionPacket.Action action) {
        return Button.builder(Component.translatable(key), button -> PacketDistributor.sendToServer(
                new MatrixControllerActionPacket(menu.token(), menu.getBlockPos(), action)))
                .bounds(x, y, width, 20)
                .build();
    }

    private void drawLine(GuiGraphics guiGraphics, Component text, int x, int y, int color) {
        guiGraphics.drawString(font, text, x, y, color, false);
    }

    private Component modeName() {
        return Component.translatable("ae2lt.matrix.mode." + menu.getMode().name().toLowerCase(Locale.ROOT));
    }

    private static String fixed(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }

    private String statusHintKey() {
        if (!menu.isFormed()) {
            return "ae2lt.matrix.gui.hint_unformed";
        }
        return switch (menu.getMode()) {
            case OVERLOAD -> {
                double heat = menu.getNormalizedHeat();
                if (heat < 0.42D) {
                    yield "ae2lt.matrix.gui.hint_cold";
                }
                if (heat > 0.58D) {
                    yield "ae2lt.matrix.gui.hint_hot";
                }
                yield "ae2lt.matrix.gui.hint_sweet";
            }
            case STABLE, QUANTUM -> {
                if (menu.getDispatchBase() < 300.0D) {
                    yield "ae2lt.matrix.gui.hint_dispatch_low";
                }
                if (menu.getBaseBatch() < 8.0D) {
                    yield "ae2lt.matrix.gui.hint_batch_low";
                }
                yield "ae2lt.matrix.gui.hint_stable";
            }
            default -> "ae2lt.matrix.gui.hint_invalid";
        };
    }

    private int hintColor() {
        return switch (statusHintKey()) {
            case "ae2lt.matrix.gui.hint_sweet", "ae2lt.matrix.gui.hint_stable" -> 0x85F29E;
            case "ae2lt.matrix.gui.hint_invalid" -> 0xFF9090;
            default -> 0xF2D37A;
        };
    }
}
