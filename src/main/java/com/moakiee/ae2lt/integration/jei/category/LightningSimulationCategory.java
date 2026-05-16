package com.moakiee.ae2lt.integration.jei.category;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.world.item.crafting.RecipeHolder;

public class LightningSimulationCategory implements IRecipeCategory<RecipeHolder<LightningSimulationRecipe>> {
    public static final IRecipeType<RecipeHolder<LightningSimulationRecipe>> TYPE =
            IRecipeType.create(ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get());

    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/lightning_simulation_room.png");

    // Crop the machine GUI work area without clipping the input slots or reaction chamber border.
    private static final int BACKGROUND_U = 5;
    private static final int BACKGROUND_V = 14;
    private static final int BACKGROUND_WIDTH = 168;
    private static final int BACKGROUND_HEIGHT = 64;
    private static final int WIDTH = BACKGROUND_WIDTH;

    private static final int SLOT_INPUT_X = 39 - BACKGROUND_U;    // 34
    private static final int SLOT_INPUT_Y = 22 - BACKGROUND_V;    // 8
    private static final int SLOT_INPUT_SPACING = 18;
    private static final int SLOT_OUTPUT_X = 119 - BACKGROUND_U;  // 114
    private static final int SLOT_OUTPUT_Y = 41 - BACKGROUND_V;   // 27

    private static final int ENERGY_TEXT_Y = BACKGROUND_HEIGHT + 2;     // 66
    private static final int LIGHTNING_TEXT_Y = BACKGROUND_HEIGHT + 12; // 76
    private static final int SUBSTITUTION_TEXT_Y = BACKGROUND_HEIGHT + 22; // 86
    private static final int HEIGHT = SUBSTITUTION_TEXT_Y + 10;         // 96, with room for the last text line

    private final IDrawable icon;
    private final IDrawable background;

    public LightningSimulationCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get()));
        this.background = guiHelper.createDrawable(
                BACKGROUND_TEXTURE, BACKGROUND_U, BACKGROUND_V, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
    }

    @Override
    public IRecipeType<RecipeHolder<LightningSimulationRecipe>> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<LightningSimulationRecipe> holder, IFocusGroup focuses) {
        var recipe = holder.value();
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
                .addIngredientsUnsafe(List.of(recipe.getResultStack()))
                .addRichTooltipCallback((recipeSlotView, tooltip) ->
                        LargeStackCountRenderer.appendCountTooltip(tooltip, recipe.getResultStack().getCount()));
    }

    @Override
    public void draw(
            RecipeHolder<LightningSimulationRecipe> holder,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphicsExtractor guiGraphics,
            double mouseX,
            double mouseY) {
        var recipe = holder.value();
        background.draw(guiGraphics);
        // Do not draw the runtime process overlay in JEI; the background already shows the static chamber.

        var font = Minecraft.getInstance().font;
        var energyText = Component.translatable(
                "jei.ae2lt.lightning_simulation.energy",
                formatCompactEnergy(recipe.totalEnergy()));
        int energyX = (WIDTH - font.width(energyText)) / 2;
        guiGraphics.text(font, energyText, energyX, ENERGY_TEXT_Y, 0xFF404040, false);
        var lightningText = Component.translatable(
                "jei.ae2lt.lightning_simulation.lightning",
                recipe.lightningCost(),
                Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage"));
        int lightningX = (WIDTH - font.width(lightningText)) / 2;
        guiGraphics.text(font, lightningText, lightningX, LIGHTNING_TEXT_Y, 0xFF404040, false);
        if (recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE) {
            var substitutionText = Component.translatable(
                    "jei.ae2lt.lightning_simulation.substitution",
                    LightningSimulationRecipeService.getEquivalentHighVoltageCost(
                            recipe.lightningTier(),
                            recipe.lightningCost()));
            int substitutionX = (WIDTH - font.width(substitutionText)) / 2;
            guiGraphics.text(font, substitutionText, substitutionX, SUBSTITUTION_TEXT_Y, 0xFF404040, false);
        }
    }

    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return ingredient.items()
                .map(holder -> new ItemStack(holder.value(), count))
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
