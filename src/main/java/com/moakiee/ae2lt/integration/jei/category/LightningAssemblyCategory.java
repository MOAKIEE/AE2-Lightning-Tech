package com.moakiee.ae2lt.integration.jei.category;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

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

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/lightning_assembly_chamber.png");

    // 截取机器 GUI 中工作区那一片用作 JEI 背景(从 (19,10) 开始,156x76)
    // 裁剪到 (19,10) 起 156x78,保证 3x3 最底行格子和右侧输出格的下边框都不被咬
    private static final int BACKGROUND_U = 19;
    private static final int BACKGROUND_V = 10;
    private static final int BACKGROUND_WIDTH = 156;
    private static final int BACKGROUND_HEIGHT = 78;

    private static final int WIDTH = BACKGROUND_WIDTH;

    // 分类坐标 = GUI 坐标 - 背景偏移
    private static final int INPUT_START_X = 29 - BACKGROUND_U; // 10
    private static final int INPUT_START_Y = 31 - BACKGROUND_V; // 21
    private static final int INPUT_SPACING = 18;
    private static final int CATALYST_X = 126 - BACKGROUND_U;   // 107
    private static final int CATALYST_Y = 16 - BACKGROUND_V;    // 6
    private static final int OUTPUT_X = 126 - BACKGROUND_U;     // 107
    private static final int OUTPUT_Y = 49 - BACKGROUND_V;      // 39

    private static final int ENERGY_TEXT_Y = BACKGROUND_HEIGHT + 2;   // 80
    private static final int LIGHTNING_TEXT_Y = BACKGROUND_HEIGHT + 12; // 90
    private static final int HEIGHT = LIGHTNING_TEXT_Y + 10;          // 100(末行文字留 10px)

    private final IDrawable icon;
    private final IDrawable background;

    public LightningAssemblyCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get()));
        this.background = guiHelper.createDrawable(
                BACKGROUND_TEXTURE, BACKGROUND_U, BACKGROUND_V, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
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
    public void setRecipe(IRecipeLayoutBuilder builder, LightningAssemblyRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size() && index < 9; index++) {
            var input = recipe.inputs().get(index);
            int col = index % 3;
            int row = index / 3;
            builder.addSlot(
                            RecipeIngredientRole.INPUT,
                            INPUT_START_X + col * INPUT_SPACING,
                            INPUT_START_Y + row * INPUT_SPACING)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
        }

        builder.addSlot(RecipeIngredientRole.CATALYST, CATALYST_X, CATALYST_Y)
                .addItemStack(new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));

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
        background.draw(guiGraphics);
        // 静态展示不再叠加运行态的进度条遮罩:
        // 一方面源贴图 (177, 48) 这块 sprite 实际是全透明的(只用于游戏内按进度揭示),
        // 另一方面和 LightningSimulationCategory 保持一致,避免在 JEI 页上画出与槽位/道具重叠的怪东西。

        var font = Minecraft.getInstance().font;
        var energyText = Component.translatable(
                "jei.ae2lt.lightning_assembly.energy",
                formatCompactEnergy(recipe.totalEnergy()));
        int energyX = (WIDTH - font.width(energyText)) / 2;
        guiGraphics.drawString(font, energyText, energyX, ENERGY_TEXT_Y, 0x404040, false);

        var lightningText = Component.translatable(
                "jei.ae2lt.lightning_assembly.lightning",
                recipe.lightningCost(),
                Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage"));
        int lightningX = (WIDTH - font.width(lightningText)) / 2;
        guiGraphics.drawString(font, lightningText, lightningX, LIGHTNING_TEXT_Y, 0x404040, false);
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
