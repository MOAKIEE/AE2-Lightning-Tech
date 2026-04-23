package com.moakiee.ae2lt.integration.jei.category;

import java.util.HashMap;
import java.util.Map;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import com.moakiee.ae2lt.lightning.strike.StructureRequirement;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * JEI category for the data-driven multiblock lightning-strike recipes.
 *
 * <p>The layout shows up to two horizontal layers ({@code y=0} and
 * {@code y=1}) of the structure as 3×3 grids, an arrow, and the produced
 * center block on the right. Rare layers (e.g. {@code y=-1}) and offsets
 * outside |x|,|z| ≤ 1 are grouped into an "additional requirements" tooltip
 * on a spacer slot so the category stays readable for simple cases while
 * still representing arbitrary structures.</p>
 */
public class LightningStrikeCategory implements IRecipeCategory<LightningStrikeRecipe> {
    public static final RecipeType<LightningStrikeRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_strike", LightningStrikeRecipe.class);

    private static final int WIDTH = 166;
    private static final int HEIGHT = 110;
    private static final int CELL = 18;

    private static final int GRID_Y1_X = 10;
    private static final int GRID_Y1_Y = 4;

    private static final int GRID_Y0_X = 10;
    private static final int GRID_Y0_Y = 60;

    private static final int ARROW_X = 76;
    private static final int ARROW_Y = 86;

    private static final int OUTPUT_X = 140;
    private static final int OUTPUT_Y = 84;

    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;

    public LightningStrikeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Blocks.LIGHTNING_ROD));
        this.arrow = guiHelper.drawableBuilder(
                        net.minecraft.resources.ResourceLocation.parse("jei:textures/jei/gui/gui_vanilla.png"),
                        82, 128, 24, 17)
                .setTextureSize(256, 256)
                .build();
    }

    @Override
    public RecipeType<LightningStrikeRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.lightning_strike.title");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LightningStrikeRecipe recipe, IFocusGroup focuses) {
        // Index requirements by (y, x, z) for the two primary layers.
        Map<Integer, Map<Long, StructureRequirement>> layerIndex = new HashMap<>();
        for (StructureRequirement req : recipe.requirements()) {
            BlockPos off = req.offset();
            layerIndex
                    .computeIfAbsent(off.getY(), k -> new HashMap<>())
                    .put(packXZ(off.getX(), off.getZ()), req);
        }

        // y = 0 primary layer — center slot is the consumed center input.
        Map<Long, StructureRequirement> layerY0 = layerIndex.getOrDefault(0, Map.of());
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = GRID_Y0_X + (dx + 1) * CELL;
                int y = GRID_Y0_Y + (dz + 1) * CELL;
                if (dx == 0 && dz == 0) {
                    builder.addSlot(RecipeIngredientRole.CATALYST, x, y)
                            .addItemStack(new ItemStack(recipe.centerInput()));
                    continue;
                }
                StructureRequirement req = layerY0.get(packXZ(dx, dz));
                if (req != null) {
                    builder.addSlot(
                                    req.consume() ? RecipeIngredientRole.INPUT : RecipeIngredientRole.CATALYST,
                                    x,
                                    y)
                            .addItemStack(new ItemStack(req.block()));
                }
            }
        }

        // y = 1: the lightning rod at (0, +1, 0) is implicit for every ritual. Always display
        // it at the center of the top layer so the user sees the full minimum structure, even
        // though the recipe data never lists it.
        int rodX = GRID_Y1_X + CELL;
        int rodY = GRID_Y1_Y + CELL;
        builder.addSlot(RecipeIngredientRole.CATALYST, rodX, rodY)
                .addItemStack(new ItemStack(Blocks.LIGHTNING_ROD));

        // Any extra requirements explicitly placed on y=1 outside center (rare) are also shown.
        Map<Long, StructureRequirement> layerY1 = layerIndex.getOrDefault(1, Map.of());
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                StructureRequirement req = layerY1.get(packXZ(dx, dz));
                if (req == null) {
                    continue;
                }
                int x = GRID_Y1_X + (dx + 1) * CELL;
                int y = GRID_Y1_Y + (dz + 1) * CELL;
                builder.addSlot(
                                req.consume() ? RecipeIngredientRole.INPUT : RecipeIngredientRole.CATALYST,
                                x,
                                y)
                        .addItemStack(new ItemStack(req.block()));
            }
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .addItemStack(new ItemStack(recipe.centerOutput()));
    }

    @Override
    public void draw(
            LightningStrikeRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        var font = Minecraft.getInstance().font;

        Component lightningLabel = recipe.requiresNaturalLightning()
                ? Component.translatable("jei.ae2lt.lightning_strike.natural_only")
                        .withStyle(ChatFormatting.DARK_PURPLE)
                : Component.translatable("jei.ae2lt.lightning_strike.any_lightning")
                        .withStyle(ChatFormatting.DARK_AQUA);
        guiGraphics.drawString(font, lightningLabel, 70, 10, TEXT_COLOR, false);

        guiGraphics.drawString(
                font,
                Component.translatable("jei.ae2lt.lightning_strike.layer_top"),
                70,
                24,
                TEXT_COLOR,
                false);
        guiGraphics.drawString(
                font,
                Component.translatable("jei.ae2lt.lightning_strike.layer_base"),
                70,
                60,
                TEXT_COLOR,
                false);

        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        // Count extra requirements outside the two rendered layers; surface as text hint.
        int extra = 0;
        for (StructureRequirement req : recipe.requirements()) {
            int y = req.offset().getY();
            int ax = Math.abs(req.offset().getX());
            int az = Math.abs(req.offset().getZ());
            if ((y != 0 && y != 1) || ax > 1 || az > 1) {
                extra++;
            }
        }
        if (extra > 0) {
            guiGraphics.drawString(
                    font,
                    Component.translatable("jei.ae2lt.lightning_strike.extra_requirements", extra),
                    70,
                    72,
                    TEXT_COLOR,
                    false);
        }
    }

    private static long packXZ(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }
}
