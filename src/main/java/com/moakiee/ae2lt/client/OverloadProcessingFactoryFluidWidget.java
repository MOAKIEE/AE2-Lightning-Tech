package com.moakiee.ae2lt.client;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.client.gui.widgets.ITooltip;

public class OverloadProcessingFactoryFluidWidget extends AbstractWidget implements ITooltip {
    private final Supplier<FluidStack> fluidSupplier;
    private final IntSupplier capacitySupplier;
    private final Component emptyMessage;
    private final int color;

    public OverloadProcessingFactoryFluidWidget(
            Supplier<FluidStack> fluidSupplier,
            IntSupplier capacitySupplier,
            Component emptyMessage,
            int color) {
        super(0, 0, 16, 58, Component.empty());
        this.fluidSupplier = fluidSupplier;
        this.capacitySupplier = capacitySupplier;
        this.emptyMessage = emptyMessage;
        this.color = color;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF4A515E);
        guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF14181D);
        guiGraphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0xFF0D1014);

        FluidStack fluid = fluidSupplier.get();
        if (fluid.isEmpty()) {
            return;
        }

        int capacity = Math.max(1, capacitySupplier.getAsInt());
        int innerHeight = height - 4;
        int filled = (int) Math.round(innerHeight * (double) fluid.getAmount() / (double) capacity);
        if (filled <= 0) {
            return;
        }

        guiGraphics.fill(
                getX() + 2,
                getY() + height - 2 - filled,
                getX() + width - 2,
                getY() + height - 2,
                color);
    }

    @Override
    public List<Component> getTooltipMessage() {
        FluidStack fluid = fluidSupplier.get();
        if (fluid.isEmpty()) {
            return List.of(emptyMessage);
        }

        return List.of(
                fluid.getHoverName(),
                Component.translatable("ae2lt.gui.overload_factory.fluid.tooltip", fluid.getAmount(), capacitySupplier.getAsInt()));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX() - 1, getY() - 1, width + 2, height + 2);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
