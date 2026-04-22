package com.moakiee.ae2lt.integration.jei;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.jei.compat.ae2jeiintegration.AE2JeiIntegrationCompat;
import com.moakiee.ae2lt.integration.jei.category.LightningAssemblyCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningTransformCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningSimulationCategory;
import com.moakiee.ae2lt.integration.jei.category.OverloadGrowthCategory;
import com.moakiee.ae2lt.integration.jei.category.OverloadProcessingCategory;
import com.moakiee.ae2lt.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

import java.util.List;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "jei_plugin");
    private static final String AE2_JEI_INTEGRATION_MODID = "ae2jeiintegration";

    public JEIPlugin() {
        if (ModList.get().isLoaded(AE2_JEI_INTEGRATION_MODID)) {
            AE2JeiIntegrationCompat.registerConverter();
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        registration.register(
                LightningJeiIngredients.TYPE,
                LightningJeiIngredients.INGREDIENTS,
                LightningJeiIngredients.HELPER,
                LightningJeiIngredients.RENDERER,
                LightningJeiIngredients.CODEC);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new OverloadGrowthCategory(guiHelper),
                new LightningAssemblyCategory(guiHelper),
                new LightningSimulationCategory(guiHelper),
                new LightningTransformCategory(guiHelper),
                new OverloadProcessingCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(OverloadGrowthCategory.TYPE, List.of(OverloadGrowthCategory.Page.values()));

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        registration.addRecipes(
                LightningAssemblyCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(
                LightningSimulationCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(
                LightningTransformCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(
                OverloadProcessingCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.toStack(), LightningAssemblyCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.toStack(), LightningSimulationCategory.TYPE);
        registration.addRecipeCatalyst(net.minecraft.world.item.Items.LIGHTNING_ROD.getDefaultInstance(), LightningTransformCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.OVERLOAD_PROCESSING_FACTORY.toStack(), OverloadProcessingCategory.TYPE);
    }
}
