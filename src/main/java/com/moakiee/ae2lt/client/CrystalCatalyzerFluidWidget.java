package com.moakiee.ae2lt.client;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.FluidBlitter;
import appeng.client.gui.widgets.ITooltip;
import appeng.core.localization.Tooltips;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * Vertical fluid tank widget for the Crystal Catalyzer.
 *
 * <p>Fluid fills the tank interior using AE2's {@link FluidBlitter} (actual fluid texture,
 * matching Advanced AE's reaction chamber), and the bright tick marks baked into the tank
 * background are redrawn on top as 1-pixel-tall strips so they remain visible regardless of
 * fluid level (without smearing the dull interior colour over the fluid).</p>
 */
public class CrystalCatalyzerFluidWidget extends AbstractWidget implements ITooltip {
    /** Inner tank width in pixels (between the bright inner frame lines). */
    public static final int TANK_INNER_WIDTH = 16;
    /** Inner tank height in pixels (y=18..y=70 inclusive inside the inner frame). */
    public static final int TANK_INNER_HEIGHT = 53;

    /** Widget origin is absolute (26, 18) in the GUI background texture. */
    private static final int TANK_SRC_X = 26;
    private static final int TANK_SRC_Y = 18;

    /** Minor tick: 3 bright pixels wide at x=39..41, every 10 rows starting y=21. */
    private static final int MINOR_TICK_SRC_X = 39;
    private static final int MINOR_TICK_WIDTH = 3;
    private static final int[] MINOR_TICK_SRC_YS = { 21, 31, 41, 51, 61 };

    /** Major tick: 5 bright pixels wide at x=37..41, every 10 rows starting y=26. */
    private static final int MAJOR_TICK_SRC_X = 37;
    private static final int MAJOR_TICK_WIDTH = 5;
    private static final int[] MAJOR_TICK_SRC_YS = { 26, 36, 46, 56, 66 };

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/crystal_catalyzer.png");

    private final Supplier<FluidStack> fluidSupplier;
    private final IntSupplier capacitySupplier;

    public CrystalCatalyzerFluidWidget(
            Supplier<FluidStack> fluidSupplier,
            IntSupplier capacitySupplier) {
        super(0, 0, TANK_INNER_WIDTH, TANK_INNER_HEIGHT, Component.empty());
        this.fluidSupplier = fluidSupplier;
        this.capacitySupplier = capacitySupplier;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        FluidStack fluid = fluidSupplier.get();
        if (!fluid.isEmpty()) {
            int capacity = Math.max(1, capacitySupplier.getAsInt());
            int filled = (int) Math.round((double) height * (double) fluid.getAmount() / (double) capacity);
            filled = Math.max(0, Math.min(filled, height));
            if (filled > 0) {
                // FluidBlitter.dest(w, h) stretches a 16x16 fluid texture to fit the
                // destination. Our inner tank is 53 px tall, so a single dest call
                // would vertically stretch the fluid. Tile the texture bottom-up in
                // 16-px chunks instead, keeping its native aspect ratio. Only the
                // final (top) chunk gets clipped when filled is not a multiple of 16.
                int y = getY() + height;
                int remaining = filled;
                while (remaining > 0) {
                    int chunk = Math.min(16, remaining);
                    y -= chunk;
                    FluidBlitter.create(fluid)
                            .dest(getX(), y, width, chunk)
                            .blit(guiGraphics);
                    remaining -= chunk;
                }
            }
        }

        // 把刻度行从原贴图挑出来(只 1px 高的亮色行),盖到流体最上层,
        // 模仿 AdvAE 反应仓的"水填满内腔 + 刻度叠在水之上"的视觉效果。
        for (int srcY : MINOR_TICK_SRC_YS) {
            drawTick(guiGraphics, MINOR_TICK_SRC_X, srcY, MINOR_TICK_WIDTH);
        }
        for (int srcY : MAJOR_TICK_SRC_YS) {
            drawTick(guiGraphics, MAJOR_TICK_SRC_X, srcY, MAJOR_TICK_WIDTH);
        }
    }

    private void drawTick(GuiGraphics guiGraphics, int srcX, int srcY, int tickWidth) {
        int offsetX = srcX - TANK_SRC_X;
        int offsetY = srcY - TANK_SRC_Y;
        Blitter.texture(BACKGROUND_TEXTURE)
                .src(srcX, srcY, tickWidth, 1)
                .dest(getX() + offsetX, getY() + offsetY, tickWidth, 1)
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        FluidStack fluid = fluidSupplier.get();
        int capacity = capacitySupplier.getAsInt();
        if (fluid.isEmpty()) {
            return List.of(
                    Component.translatable("ae2lt.gui.crystal_catalyzer.fluid.tooltip", 0, capacity)
                            .withStyle(Tooltips.NUMBER_TEXT));
        }

        return List.of(
                fluid.getHoverName(),
                Component.empty(),
                Component.translatable("ae2lt.gui.crystal_catalyzer.fluid.tooltip", fluid.getAmount(), capacity)
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
