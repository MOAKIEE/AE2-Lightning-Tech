package com.moakiee.ae2lt.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;

/**
 * AE2 {@link TabButton} (BOX style) that overlays a custom 16×16 PNG on the
 * engine's metallic tab frame, giving mod buttons the same look as AE2's native
 * tab and toolbar buttons.
 *
 * <p>Passing {@code null} as the base {@link Icon} makes the super class skip
 * its own glyph blit (it short-circuits on {@code icon == null}), leaving the
 * frame clean for our overlay. The hover highlight piggy-backs on AE2's FOCUS
 * sprite by reporting {@code isFocused()} while hovered — the same trick the
 * frequency screen's tabs use.</p>
 */
public final class IconTabButton extends TabButton {

    private final ResourceLocation icon;

    public IconTabButton(ResourceLocation icon, Component tooltip, Button.OnPress onPress) {
        super((Icon) null, tooltip, onPress);
        this.icon = icon;
        setStyle(Style.BOX);
    }

    @Override
    public boolean isFocused() {
        return super.isFocused() || isHovered();
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        if (icon != null) {
            // Match AE2's (+2, +1) BOX-style icon offset so the overlay aligns
            // with how the base class would have drawn an Icon glyph.
            guiGraphics.blit(icon, getX() + 2, getY() + 1, 0, 0, 16, 16, 16, 16);
        }
    }
}
