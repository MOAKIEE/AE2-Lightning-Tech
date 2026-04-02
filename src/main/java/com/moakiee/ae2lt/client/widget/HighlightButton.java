package com.moakiee.ae2lt.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A button that highlights a remote block position in the world when clicked.
 */
public class HighlightButton extends Button {

    @Nullable
    private BlockPos target;
    @Nullable
    private ResourceKey<Level> dimension;
    private float multiplier = 1.0f;

    public HighlightButton(int x, int y) {
        super(x, y, 16, 16,
                Component.translatable("gui.ae2lt.wireless.highlight"),
                HighlightButton::onClicked,
                DEFAULT_NARRATION);
        setTooltip(Tooltip.create(Component.translatable("gui.ae2lt.wireless.highlight.tooltip")));
    }

    public void setTarget(@Nullable BlockPos pos, @Nullable ResourceKey<Level> dim) {
        this.target = pos;
        this.dimension = dim;
    }

    public void setMultiplier(double val) {
        this.multiplier = (float) Math.max(1, Math.min(val, 30));
    }

    private static void onClicked(Button btn) {
        if (btn instanceof HighlightButton hb && hb.target != null && hb.dimension != null) {
            long duration = (long) (600 * hb.multiplier);
            WirelessHighlightRenderer.highlight(hb.target, hb.dimension, duration);
        }
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw a simple "H" button
        int bgColor = this.isHovered() ? 0xFF4080FF : 0xFF303030;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
        graphics.renderOutline(getX(), getY(), width, height, 0xFFFFFFFF);
        graphics.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                "H", getX() + width / 2, getY() + (height - 8) / 2,
                this.active ? 0xFFFFFF : 0x808080);
    }
}
