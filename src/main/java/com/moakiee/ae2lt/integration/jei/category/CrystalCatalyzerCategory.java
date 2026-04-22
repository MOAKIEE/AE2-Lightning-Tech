package com.moakiee.ae2lt.integration.jei.category;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModBlocks;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 水晶催化器的 JEI 占位页面。
 * 过载水晶成长机制仍可在 {@link OverloadGrowthCategory} 中查看;这里只是给
 * TODO 留一个入口,等后续确认催化器专属的加速/配方表后再补齐。
 */
public class CrystalCatalyzerCategory extends AbstractRecipeCategory<CrystalCatalyzerCategory.Page> {
    public static final RecipeType<Page> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "crystal_catalyzer", Page.class);

    private static final int WIDTH = 150;
    private static final int HEIGHT = 60;
    private static final int BODY_COLOR = 0xFF404040;

    public CrystalCatalyzerCategory(IGuiHelper guiHelper) {
        super(
                TYPE,
                Component.translatable("jei.ae2lt.crystal_catalyzer.title"),
                guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.CRYSTAL_CATALYZER.get())),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Page recipe, IFocusGroup focuses) {
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Page recipe, IFocusGroup focuses) {
        builder.addText(Component.translatable("jei.ae2lt.crystal_catalyzer.todo"), WIDTH, HEIGHT)
                .setPosition(0, 0)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setTextAlignment(VerticalAlignment.CENTER)
                .setLineSpacing(2)
                .setColor(BODY_COLOR);
    }

    public enum Page {
        PLACEHOLDER
    }
}
