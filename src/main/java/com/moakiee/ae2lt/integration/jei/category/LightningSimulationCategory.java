package com.moakiee.ae2lt.integration.jei.category;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService;
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

public class LightningSimulationCategory implements IRecipeCategory<LightningSimulationRecipe> {
    public static final RecipeType<LightningSimulationRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_simulation", LightningSimulationRecipe.class);

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/lightning_simulation_room.png");

    private static final int BACKGROUND_U = 5;
    private static final int BACKGROUND_V = 15;
    private static final int WIDTH = 168;
    private static final int HEIGHT = 75;
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int SLOT_INPUT_X = 34;
    private static final int SLOT_INPUT_Y = 7;
    private static final int SLOT_INPUT_SPACING = 18;
    private static final int SLOT_OUTPUT_X = 114;
    private static final int SLOT_OUTPUT_Y = 26;
    private static final int PROCESS_X = 53;
    private static final int PROCESS_Y = 10;
    private static final int ENERGY_TEXT_Y = 58;
    private static final int LIGHTNING_TEXT_Y = 68;
    private static final int SUBSTITUTION_TEXT_Y = 78;
    private static final int PROCESS_WIDTH = 50;
    private static final int PROCESS_HEIGHT = 46;
    private static final int PROCESS_STAGE_COUNT = 20;
    private static final long PROCESS_CYCLE_MS = 2_000L;
    private static final int PROCESS_OVERLAY_U = 177;
    private static final int PROCESS_OVERLAY_V = 48;

    private final IDrawable icon;
    private final IDrawable background;

    public LightningSimulationCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get()));
        this.background = guiHelper.createDrawable(BACKGROUND_TEXTURE, BACKGROUND_U, BACKGROUND_V, WIDTH, HEIGHT);
    }

    @Override
    public RecipeType<LightningSimulationRecipe> getRecipeType() {
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
        return Component.translatable("jei.ae2lt.lightning_simulation.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LightningSimulationRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size(); index++) {
            var input = recipe.inputs().get(index);
            builder.addSlot(RecipeIngredientRole.INPUT, SLOT_INPUT_X, SLOT_INPUT_Y + index * SLOT_INPUT_SPACING)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, SLOT_OUTPUT_X, SLOT_OUTPUT_Y)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(recipe.getResultStack())
                .addRichTooltipCallback((recipeSlotView, tooltip) ->
                        LargeStackCountRenderer.appendCountTooltip(tooltip, recipe.getResultStack().getCount()));
    }

    @Override
    public void draw(
            LightningSimulationRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        background.draw(guiGraphics);
        drawProcessOverlay(guiGraphics);

        var font = Minecraft.getInstance().font;
        var energyText = Component.translatable(
                "jei.ae2lt.lightning_simulation.energy",
                formatCompactEnergy(recipe.totalEnergy()));
        int energyX = (WIDTH - font.width(energyText)) / 2;
        guiGraphics.drawString(font, energyText, energyX, ENERGY_TEXT_Y, 0x404040, false);
        var lightningText = Component.translatable(
                "jei.ae2lt.lightning_simulation.lightning",
                recipe.lightningCost(),
                Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage"));
        int lightningX = (WIDTH - font.width(lightningText)) / 2;
        guiGraphics.drawString(font, lightningText, lightningX, LIGHTNING_TEXT_Y, 0x404040, false);
        if (recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE) {
            var substitutionText = Component.translatable(
                    "jei.ae2lt.lightning_simulation.substitution",
                    LightningSimulationRecipeService.getEquivalentHighVoltageCost(
                            recipe.lightningTier(),
                            recipe.lightningCost()));
            int substitutionX = (WIDTH - font.width(substitutionText)) / 2;
            guiGraphics.drawString(font, substitutionText, substitutionX, SUBSTITUTION_TEXT_Y, 0x404040, false);
        }
    }

    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }

    private void drawProcessOverlay(GuiGraphics guiGraphics) {
        long elapsed = Util.getMillis() % PROCESS_CYCLE_MS;
        double progress = elapsed / (double) PROCESS_CYCLE_MS;
        int stage = progress <= 0.0D
                ? 0
                : Mth.clamp((int) Math.ceil(progress * PROCESS_STAGE_COUNT), 1, PROCESS_STAGE_COUNT);
        if (stage <= 0) {
            return;
        }

        int rows = Mth.clamp(Mth.ceil(PROCESS_HEIGHT * stage / (float) PROCESS_STAGE_COUNT), 0, PROCESS_HEIGHT);
        int topRows = rows / 2;
        int bottomRows = rows - topRows;

        if (topRows > 0) {
            guiGraphics.blit(
                    BACKGROUND_TEXTURE,
                    PROCESS_X,
                    PROCESS_Y,
                    PROCESS_OVERLAY_U,
                    PROCESS_OVERLAY_V,
                    PROCESS_WIDTH,
                    topRows,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
        }

        if (bottomRows > 0) {
            int srcY = PROCESS_OVERLAY_V + PROCESS_HEIGHT - bottomRows;
            int destY = PROCESS_Y + PROCESS_HEIGHT - bottomRows;
            guiGraphics.blit(
                    BACKGROUND_TEXTURE,
                    PROCESS_X,
                    destY,
                    PROCESS_OVERLAY_U,
                    srcY,
                    PROCESS_WIDTH,
                    bottomRows,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
        }
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
