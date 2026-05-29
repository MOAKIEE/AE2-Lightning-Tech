package com.moakiee.ae2lt.integration.jei.category;

import java.util.Arrays;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.integration.jei.LightningJeiIngredientRenderer;
import com.moakiee.ae2lt.integration.jei.LightningJeiIngredients;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class LightningTransformCategory implements IRecipeCategory<LightningTransformRecipe> {
    public static final RecipeType<LightningTransformRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_transform", LightningTransformRecipe.class);

    private static final int WIDTH = 134;
    private static final int HEIGHT = 66;
    private static final int INPUT_START_X = 5;
    private static final int INPUT_START_Y = 5;
    private static final int INPUT_SLOT_PITCH = 20;
    private static final int CATALYST_X = 56;
    private static final int CATALYST_Y = 25;
    private static final int OUTPUT_X = 110;
    private static final int OUTPUT_Y = 25;
    private static final int ARROW_LEFT_X = 28;
    private static final int ARROW_RIGHT_X = 81;
    private static final int ARROW_Y = 24;
    private static final int LABEL_Y = 4;
    private static final int TEXT_COLOR = 0x404040;

    private static final ResourceLocation RECIPE_TEXTURE =
            new ResourceLocation("jei", "textures/gui/gui.png");
    private static final int ARROW_U = 82;
    private static final int ARROW_V = 128;
    private static final int ARROW_W = 24;
    private static final int ARROW_H = 16;
    private static final long ARROW_CYCLE_MS = 250L;

    private final IDrawable background;
    private final IDrawable icon;

    public LightningTransformCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(LightningJeiIngredients.TYPE, LightningKey.HIGH_VOLTAGE);
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
        int inputCount = recipe.inputs().size();
        int x = INPUT_START_X;
        int y = INPUT_START_Y;
        if (inputCount < 3) {
            y += (3 - inputCount) * INPUT_SLOT_PITCH / 2;
        }
        for (int index = 0; index < inputCount; index++) {
            var input = recipe.inputs().get(index);
            builder.addSlot(RecipeIngredientRole.INPUT, x + 1, y + 1)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
            y += INPUT_SLOT_PITCH;
            if (y >= INPUT_START_Y + INPUT_SLOT_PITCH * 3) {
                y -= INPUT_SLOT_PITCH * 3;
                x += 18;
            }
        }

        builder.addSlot(RecipeIngredientRole.CATALYST, CATALYST_X + 1, CATALYST_Y + 1)
                .setCustomRenderer(LightningJeiIngredients.TYPE, LightningJeiIngredientRenderer.NO_TOOLTIP)
                .addIngredient(LightningJeiIngredients.TYPE, LightningKey.HIGH_VOLTAGE);

        var resultStack = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, OUTPUT_Y + 1)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(resultStack)
                .addTooltipCallback((recipeSlotView, tooltip) ->
                        LargeStackCountRenderer.appendCountTooltip(tooltip, resultStack.getCount()));
    }

    @Override
    public void draw(
            LightningTransformRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        var font = Minecraft.getInstance().font;
        var label = Component.translatable("jei.ae2lt.lightning_transform.label");
        int labelX = (WIDTH - font.width(label)) / 2;
        guiGraphics.drawString(font, label, labelX, LABEL_Y, TEXT_COLOR, false);

        // Draw recipe arrows
        drawRecipeArrow(guiGraphics, ARROW_LEFT_X, ARROW_Y);
        drawRecipeArrow(guiGraphics, ARROW_RIGHT_X, ARROW_Y);
    }

    private static void drawRecipeArrow(GuiGraphics guiGraphics, int x, int y) {
        long elapsed = Util.getMillis() % ARROW_CYCLE_MS;
        double progress = elapsed / (double) ARROW_CYCLE_MS;
        int fillW = Mth.clamp((int) Math.ceil(progress * ARROW_W), 0, ARROW_W);
        if (fillW <= 0) {
            return;
        }
        guiGraphics.blit(
                RECIPE_TEXTURE,
                x, y,
                ARROW_U, ARROW_V,
                fillW, ARROW_H,
                256, 256);
    }

    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> {
                    ItemStack copy = stack.copy();
                    copy.setCount(count);
                    return copy;
                })
                .toList();
    }
}
