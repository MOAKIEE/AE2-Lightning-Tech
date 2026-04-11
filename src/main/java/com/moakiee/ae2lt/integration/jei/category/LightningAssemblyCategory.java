package com.moakiee.ae2lt.integration.jei.category;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

public class LightningAssemblyCategory implements IRecipeCategory<LightningAssemblyRecipe> {
    public static final RecipeType<LightningAssemblyRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_assembly", LightningAssemblyRecipe.class);

    private static final int WIDTH = 180;
    private static final int HEIGHT = 92;
    private static final int INPUT_START_X = 6;
    private static final int INPUT_START_Y = 18;
    private static final int SLOT_SPACING = 18;
    private static final int OUTPUT_X = 148;
    private static final int OUTPUT_Y = 36;
    private static final int ENERGY_TEXT_Y = 2;
    private static final int LIGHTNING_TEXT_Y = 12;
    private static final int ARROW_X = 128;
    private static final int ARROW_Y = 40;

    private final IDrawable icon;
    private final IDrawable background;

    public LightningAssemblyCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get()));
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
    }

    @Override
    public RecipeType<LightningAssemblyRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.lightning_assembly.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LightningAssemblyRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size(); index++) {
            var input = recipe.inputs().get(index);
            int x = INPUT_START_X + (index % 3) * SLOT_SPACING;
            int y = INPUT_START_Y + (index / 3) * SLOT_SPACING;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(recipe.getResultStack())
                .addRichTooltipCallback((recipeSlotView, tooltip) ->
                        LargeStackCountRenderer.appendCountTooltip(tooltip, recipe.getResultStack().getCount()));
    }

    @Override
    public void draw(
            LightningAssemblyRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        var font = Minecraft.getInstance().font;
        var energyText = Component.translatable(
                "jei.ae2lt.lightning_assembly.energy",
                formatCompactEnergy(recipe.totalEnergy()));
        guiGraphics.drawString(font, energyText, 0, ENERGY_TEXT_Y, 0x404040, false);

        var lightningText = Component.translatable(
                "jei.ae2lt.lightning_assembly.lightning",
                recipe.lightningCost(),
                Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage"));
        guiGraphics.drawString(font, lightningText, 0, LIGHTNING_TEXT_Y, 0x404040, false);

        guiGraphics.drawString(font, "=>", ARROW_X, ARROW_Y, 0x808080, false);

    }

    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }

    private static String formatCompactEnergy(long energy) {
        if (energy >= 1_000_000L) {
            return formatCompactValue(energy / 1_000_000D, "m");
        }
        if (energy >= 1_000L) {
            return formatCompactValue(energy / 1_000D, "k");
        }
        return Long.toString(energy);
    }

    private static String formatCompactValue(double value, String suffix) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return Long.toString(Math.round(rounded)) + suffix;
        }
        return rounded + suffix;
    }
}
