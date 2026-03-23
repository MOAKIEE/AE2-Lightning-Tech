package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AE2LightningTech.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, AE2LightningTech.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LightningTransformRecipe>>
            LIGHTNING_TRANSFORM_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_transform", LightningTransformRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<LightningTransformRecipe>> LIGHTNING_TRANSFORM_TYPE =
            RECIPE_TYPES.register(
                    "lightning_transform",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "lightning_transform")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LightningSimulationRecipe>>
            LIGHTNING_SIMULATION_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_simulation", LightningSimulationRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<LightningSimulationRecipe>> LIGHTNING_SIMULATION_TYPE =
            RECIPE_TYPES.register(
                    "lightning_simulation",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "lightning_simulation")));

    private ModRecipeTypes() {
    }
}
