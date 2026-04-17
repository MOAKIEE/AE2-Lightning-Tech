package com.moakiee.ae2lt.integration.jei.category;

import java.util.Arrays;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

public class LightningTransformCategory implements IRecipeCategory<LightningTransformRecipe> {
    public static final RecipeType<LightningTransformRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_transform", LightningTransformRecipe.class);

    private static final int WIDTH = 166;
    private static final int HEIGHT = 82;
    private static final int INPUT_X = 10;
    private static final int INPUT_Y = 6;
    private static final int INPUT_SPACING = 18;
    private static final int OUTPUT_X = 138;
    private static final int OUTPUT_Y = 30;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable background;
    private final IDrawable icon;

    public LightningTransformCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Blocks.LIGHTNING_ROD));
    }

    @Override
    public RecipeType<LightningTransformRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.lightning_transform.title");
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
    public void setRecipe(IRecipeLayoutBuilder builder, LightningTransformRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size(); index++) {
            var input = recipe.inputs().get(index);
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y + index * INPUT_SPACING)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
        }

        var resultStack = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(resultStack)
                .addRichTooltipCallback((recipeSlotView, tooltip) ->
                        LargeStackCountRenderer.appendCountTooltip(tooltip, resultStack.getCount()));
    }

    @Override
    public void draw(
            LightningTransformRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        background.draw(guiGraphics);

        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(
                font,
                Component.translatable("jei.ae2lt.lightning_transform.step_1"),
                34,
                16,
                TEXT_COLOR,
                false);
        guiGraphics.drawString(
                font,
                Component.translatable("jei.ae2lt.lightning_transform.step_2"),
                34,
                28,
                TEXT_COLOR,
                false);
        guiGraphics.drawString(font, "===>", 96, 34, TEXT_COLOR, false);
        guiGraphics.drawString(
                font,
                Component.translatable("jei.ae2lt.lightning_transform.tip"),
                10,
                62,
                TEXT_COLOR,
                false);
    }

    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }
}
