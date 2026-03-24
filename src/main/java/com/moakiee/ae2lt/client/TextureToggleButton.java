package com.moakiee.ae2lt.client;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

import com.moakiee.ae2lt.AE2LightningTech;

public class TextureToggleButton extends Button implements ITooltip {

    private final ResourceLocation textureOn;
    private final ResourceLocation textureOff;
    private final Listener listener;

    private List<Component> tooltipOn = Collections.emptyList();
    private List<Component> tooltipOff = Collections.emptyList();
    private boolean state;

    public TextureToggleButton(ButtonType type, Listener listener) {
        super(0, 0, 16, 16, Component.empty(), btn -> listener.onChange(false), DEFAULT_NARRATION);
        this.textureOn = type.textureOn;
        this.textureOff = type.textureOff;
        this.listener = listener;
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                AE2LightningTech.MODID, "textures/gui/buttons/" + path + ".png");
    }

    public void setTooltipOn(List<Component> lines) {
        this.tooltipOn = lines;
    }

    public void setTooltipOff(List<Component> lines) {
        this.tooltipOff = lines;
    }

    public void setState(boolean isOn) {
        this.state = isOn;
    }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        this.active = visible;
    }

    @Override
    public void onPress() {
        this.listener.onChange(!this.state);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        var yOffset = isHovered() ? 1 : 0;
        var background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;

        background.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        var blitter = Blitter.texture(this.state ? this.textureOn : this.textureOff, 16, 16)
                .src(0, 0, 16, 16);
        if (!this.active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return this.state ? this.tooltipOn : this.tooltipOff;
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible && !getTooltipMessage().isEmpty();
    }

    public enum ButtonType {
        MODE(texture("wireless_mode"), texture("wired_mode")),
        AUTO_RETURN(texture("auto_return_on"), texture("auto_return_off")),
        WIRELESS_STRATEGY(texture("even_distribution"), texture("single_target")),
        FILTERED_IMPORT(texture("filtered_import_on"), texture("filtered_import_off")),
        SPEED(texture("speed_fast"), texture("speed_normal")),
        AUTO_EXPORT(texture("auto_return_on"), texture("auto_return_off")),
        AUTO_IMPORT(texture("filtered_import_on"), texture("filtered_import_off")),
        EJECT(texture("auto_return_on"), texture("auto_return_off"));

        private final ResourceLocation textureOn;
        private final ResourceLocation textureOff;

        ButtonType(ResourceLocation textureOn, ResourceLocation textureOff) {
            this.textureOn = textureOn;
            this.textureOff = textureOff;
        }
    }

    @FunctionalInterface
    public interface Listener {
        void onChange(boolean state);
    }
}
