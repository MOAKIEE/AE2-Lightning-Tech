package com.moakiee.ae2lt.client;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.fml.ModList;

import appeng.client.gui.style.Blitter;
import appeng.core.localization.Tooltips;
import appeng.client.gui.widgets.ITooltip;

public class OverloadProcessingFactoryFluidWidget extends AbstractWidget implements ITooltip {
    private final Supplier<FluidStack> fluidSupplier;
    private final IntSupplier capacitySupplier;

    private Fluid cachedFluid;
    private TextureAtlasSprite cachedSprite;

    public OverloadProcessingFactoryFluidWidget(
            Supplier<FluidStack> fluidSupplier,
            IntSupplier capacitySupplier) {
        super(0, 0, 16, 54, Component.empty());
        this.fluidSupplier = fluidSupplier;
        this.capacitySupplier = capacitySupplier;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        FluidStack fluid = fluidSupplier.get();
        if (fluid.isEmpty()) {
            return;
        }

        int capacity = Math.max(1, capacitySupplier.getAsInt());
        int filled = (int) Math.round(height * (double) fluid.getAmount() / (double) capacity);
        if (filled <= 0) {
            return;
        }
        filled = Math.min(filled, height);

        TextureAtlasSprite sprite = resolveSprite(fluid);
        if (sprite == null) {
            return;
        }

        var attributes = IClientFluidTypeExtensions.of(fluid.getFluid());
        Blitter blitter = Blitter.sprite(sprite)
                .colorRgb(attributes.getTintColor(fluid))
                .blending(true);

        int x = getX();
        int yBottom = getY() + height;
        int drawn = 0;
        while (drawn < filled) {
            int sliceH = Math.min(width, filled - drawn);
            blitter.dest(x, yBottom - drawn - sliceH, width, sliceH).blit(guiGraphics);
            drawn += sliceH;
        }
    }

    private TextureAtlasSprite resolveSprite(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        if (fluid != cachedFluid) {
            cachedFluid = fluid;
            var attributes = IClientFluidTypeExtensions.of(fluid);
            cachedSprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(attributes.getStillTexture(stack));
        }
        return cachedSprite;
    }

    @Override
    public List<Component> getTooltipMessage() {
        FluidStack fluid = fluidSupplier.get();
        int capacity = capacitySupplier.getAsInt();
        if (fluid.isEmpty()) {
            return List.of(
                    Component.translatable("ae2lt.gui.overload_factory.fluid.tooltip", 0, capacity)
                            .withStyle(Tooltips.NUMBER_TEXT));
        }

        return List.of(
                fluid.getHoverName(),
                Component.empty(),
                Component.translatable("ae2lt.gui.overload_factory.fluid.tooltip", fluid.getAmount(), capacity)
                        .withStyle(Tooltips.NUMBER_TEXT),
                Component.empty(),
                Component.literal(getModDisplayName(fluid))
                        .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), width, height);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    private static String getModDisplayName(FluidStack fluid) {
        var key = fluid.getFluid() == Fluids.EMPTY
                ? null
                : fluid.getFluid().builtInRegistryHolder().key().location();
        if (key == null) {
            return "Minecraft";
        }

        var namespace = key.getNamespace();
        if ("c".equals(namespace)) {
            return "Common";
        }

        return ModList.get()
                .getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElseGet(() -> namespace.replace('_', ' '));
    }
}
