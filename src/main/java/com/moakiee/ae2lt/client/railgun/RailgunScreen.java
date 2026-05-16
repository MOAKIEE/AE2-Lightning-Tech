package com.moakiee.ae2lt.client.railgun;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.logic.railgun.RailgunBinding;
import com.moakiee.ae2lt.menu.railgun.RailgunSettingsMenu;

public class RailgunScreen extends AbstractContainerScreen<RailgunSettingsMenu> {

    private static final int BTN_X = 116;
    private static final int BTN_TERRAIN_Y = 22;
    private static final int BTN_PVP_Y = 44;
    private static final int BTN_W = 52;
    private static final int BTN_H = 16;

    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 24;
    private static final int ENERGY_W = 104;
    private static final int ENERGY_H = 10;
    private static final int NETWORK_X = 8;
    private static final int NETWORK_Y = 46;
    private static final int MODULES_X = 8;
    private static final int MODULES_Y = 62;

    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 104;

    private static final int COLOR_LABEL = 0xFFE0E0E0;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;
    private static final int COLOR_BTN_ON_TEXT = 0xFFFFFFFF;
    private static final int COLOR_BTN_OFF_TEXT = 0xFFCCCCCC;

    private int networkLabelWidth = 0;

    public RailgunScreen(RailgunSettingsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 188;
        this.inventoryLabelY = 93;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        gfx.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF1E1E1E);
        gfx.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFF313131);

        drawEnergyBar(gfx, x + ENERGY_X, y + ENERGY_Y);
        // Network status panel.
        gfx.fill(x + NETWORK_X - 2, y + NETWORK_Y - 3,
                x + this.imageWidth - 8, y + NETWORK_Y + 11, 0xFF1B1B1B);
        drawToggleBg(gfx, x + BTN_X, y + BTN_TERRAIN_Y, menu.terrainDestruction, 0xFFCC4444);
        drawToggleBg(gfx, x + BTN_X, y + BTN_PVP_Y, menu.pvpLock, 0xFF4488CC);
        drawPlayerInventory(gfx, x + PLAYER_INV_X, y + PLAYER_INV_Y);
    }

    private void drawEnergyBar(GuiGraphics gfx, int x, int y) {
        gfx.fill(x - 1, y - 1, x + ENERGY_W + 1, y + ENERGY_H + 1, 0xFF3C3C3C);
        gfx.fill(x, y, x + ENERGY_W, y + ENERGY_H, 0xFF1F2A22);
        int filled = 0;
        if (menu.bufferCapacity > 0L) {
            double ratio = Math.min(1.0D, Math.max(0.0D, (double) menu.bufferStored / (double) menu.bufferCapacity));
            filled = (int) Math.round(ratio * ENERGY_W);
        }
        if (filled > 0) {
            gfx.fill(x, y, x + filled, y + ENERGY_H, 0xFF36B65C);
        }
    }

    private static void drawPlayerInventory(GuiGraphics gfx, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotTile(gfx, x + col * 18, y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotTile(gfx, x + col * 18, y + 58);
        }
    }

    private static void drawSlotTile(GuiGraphics gfx, int x, int y) {
        gfx.fill(x - 1, y - 1, x + 17, y + 17, 0xFF080808);
        gfx.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        gfx.fill(x + 1, y + 1, x + 15, y + 15, 0xFF1B1B1B);
    }

    private static void drawToggleBg(GuiGraphics gfx, int x, int y, boolean on, int onColor) {
        int border = on ? onColor : 0xFF555555;
        int fill = on ? darken(onColor) : 0xFF2A2A2A;
        gfx.fill(x - 1, y - 1, x + BTN_W + 1, y + BTN_H + 1, border);
        gfx.fill(x, y, x + BTN_W, y + BTN_H, fill);
    }

    private static int darken(int argb) {
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r * 0.55);
        g = (int) (g * 0.55);
        b = (int) (b * 0.55);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFE0E0E0, false);
        gfx.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFE0E0E0, false);

        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.energy.title"),
                ENERGY_X, ENERGY_Y - 10, COLOR_LABEL, false);
        gfx.drawString(this.font,
                Component.literal(formatEnergy(menu.bufferStored) + " / " + formatEnergy(menu.bufferCapacity)),
                ENERGY_X, ENERGY_Y + 14, COLOR_LABEL_DIM, false);

        Component networkLine = buildNetworkStatus();
        gfx.drawString(this.font, networkLine, NETWORK_X, NETWORK_Y, COLOR_LABEL, false);
        this.networkLabelWidth = this.font.width(networkLine);

        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.section.modules"),
                MODULES_X, MODULES_Y, COLOR_LABEL, false);
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.module.core",
                        menu.coreInstalled != 0
                                ? Component.translatable("ae2lt.railgun.gui.state.installed")
                                : Component.translatable("ae2lt.railgun.gui.state.missing")),
                MODULES_X, MODULES_Y + 12, COLOR_LABEL_DIM, false);
        gfx.drawString(this.font,
                Component.translatable("ae2lt.railgun.gui.module.summary",
                        menu.computeCount,
                        menu.accelCount,
                        menu.overloadExecInstalled != 0
                                ? Component.translatable("ae2lt.railgun.gui.state.installed")
                                : Component.translatable("ae2lt.railgun.gui.state.missing")),
                MODULES_X, MODULES_Y + 24, COLOR_LABEL_DIM, false);

        drawToggleLabel(gfx, BTN_X, BTN_TERRAIN_Y,
                "ae2lt.railgun.gui.button.terrain", menu.terrainDestruction);
        drawToggleLabel(gfx, BTN_X, BTN_PVP_Y,
                "ae2lt.railgun.gui.button.pvp_lock", menu.pvpLock);
    }

    private static String formatEnergy(long value) {
        return String.format(Locale.ROOT, "%,d AE", Math.max(0L, value));
    }

    private void drawToggleLabel(GuiGraphics gfx, int relX, int relY, String labelKey, boolean on) {
        Component label = Component.translatable(labelKey);
        Component state = on
                ? Component.translatable("ae2lt.railgun.gui.state.on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("ae2lt.railgun.gui.state.off").withStyle(ChatFormatting.RED);
        Component combined = Component.empty().append(label).append(" ").append(state);
        int textW = this.font.width(combined);
        int textX = relX + (BTN_W - textW) / 2;
        int textY = relY + 4;
        int textColor = on ? COLOR_BTN_ON_TEXT : COLOR_BTN_OFF_TEXT;
        gfx.drawString(this.font, combined, textX, textY, textColor, false);
    }

    private Component buildNetworkStatus() {
        Component title = Component.translatable("ae2lt.railgun.gui.network.title");
        if (!menu.isBound()) {
            return Component.empty()
                    .append(title)
                    .append(": ")
                    .append(Component.translatable("ae2lt.railgun.gui.network.unbound")
                            .withStyle(ChatFormatting.RED));
        }
        return Component.empty()
                .append(title)
                .append(": ")
                .append(Component.translatable("ae2lt.railgun.gui.network.bound_dimension", menu.boundDimensionLabel)
                        .withStyle(ChatFormatting.GREEN));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int gx = (this.width - this.imageWidth) / 2;
            int gy = (this.height - this.imageHeight) / 2;
            if (inRect(mouseX, mouseY, gx + BTN_X, gy + BTN_TERRAIN_Y, BTN_W, BTN_H)) {
                menu.clientToggleTerrain();
                playClick();
                return true;
            }
            if (inRect(mouseX, mouseY, gx + BTN_X, gy + BTN_PVP_Y, BTN_W, BTN_H)) {
                menu.clientTogglePvpLock();
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    protected void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        super.renderTooltip(gfx, mouseX, mouseY);
        int gx = (this.width - this.imageWidth) / 2;
        int gy = (this.height - this.imageHeight) / 2;

        if (inRect(mouseX, mouseY, gx + BTN_X, gy + BTN_TERRAIN_Y, BTN_W, BTN_H)) {
            var key = menu.terrainDestruction
                    ? "ae2lt.railgun.terrain.on"
                    : "ae2lt.railgun.terrain.off";
            gfx.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            return;
        }
        if (inRect(mouseX, mouseY, gx + BTN_X, gy + BTN_PVP_Y, BTN_W, BTN_H)) {
            var key = menu.pvpLock
                    ? "ae2lt.railgun.pvp_lock.on"
                    : "ae2lt.railgun.pvp_lock.off";
            gfx.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            return;
        }
        if (mouseY >= gy + NETWORK_Y - 1
                && mouseY <= gy + NETWORK_Y + 9
                && mouseX >= gx + NETWORK_X
                && mouseX <= gx + NETWORK_X + this.networkLabelWidth) {
            renderNetworkTooltip(gfx, mouseX, mouseY);
        }
    }

    private void renderNetworkTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        ItemStack stack = menu.host().getStack();
        GlobalPos pos = RailgunBinding.getBoundPos(stack);
        Component tip;
        if (pos == null) {
            tip = Component.translatable("ae2lt.railgun.gui.network.tooltip.unbound");
        } else {
            tip = Component.translatable("ae2lt.railgun.gui.network.tooltip.bound",
                    pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(),
                    pos.dimension().location().toString());
        }
        List<Component> lines = new ArrayList<>();
        for (String line : tip.getString().split("\n")) {
            lines.add(Component.literal(line));
        }
        gfx.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }
}
