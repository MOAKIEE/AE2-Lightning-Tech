package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
                    RECIPE_SERIALIZERS.register("lightning_transform", () -> new RecipeSerializer<>(
                            LightningTransformRecipe.Serializer.CODEC,
                            LightningTransformRecipe.Serializer.STREAM_CODEC));

    public static final DeferredHolder<RecipeType<?>, RecipeType<LightningTransformRecipe>> LIGHTNING_TRANSFORM_TYPE =
            RECIPE_TYPES.register(
                    "lightning_transform",
                    () -> RecipeType.simple(Identifier.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "lightning_transform")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LightningSimulationRecipe>>
            LIGHTNING_SIMULATION_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_simulation", () -> new RecipeSerializer<>(
                            LightningSimulationRecipe.Serializer.CODEC,
                            LightningSimulationRecipe.Serializer.STREAM_CODEC));

    public static final DeferredHolder<RecipeType<?>, RecipeType<LightningSimulationRecipe>> LIGHTNING_SIMULATION_TYPE =
            RECIPE_TYPES.register(
                    "lightning_simulation",
                    () -> RecipeType.simple(Identifier.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "lightning_simulation")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LightningAssemblyRecipe>>
            LIGHTNING_ASSEMBLY_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_assembly", () -> new RecipeSerializer<>(
                            LightningAssemblyRecipe.Serializer.CODEC,
                            LightningAssemblyRecipe.Serializer.STREAM_CODEC));

    public static final DeferredHolder<RecipeType<?>, RecipeType<LightningAssemblyRecipe>> LIGHTNING_ASSEMBLY_TYPE =
            RECIPE_TYPES.register(
                    "lightning_assembly",
                    () -> RecipeType.simple(Identifier.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "lightning_assembly")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<OverloadProcessingRecipe>>
            OVERLOAD_PROCESSING_SERIALIZER =
                    RECIPE_SERIALIZERS.register("overload_processing", () -> new RecipeSerializer<>(
                            OverloadProcessingRecipe.Serializer.CODEC,
                            OverloadProcessingRecipe.Serializer.STREAM_CODEC));

    public static final DeferredHolder<RecipeType<?>, RecipeType<OverloadProcessingRecipe>> OVERLOAD_PROCESSING_TYPE =
            RECIPE_TYPES.register(
                    "overload_processing",
                    () -> RecipeType.simple(Identifier.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "overload_processing")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CrystalCatalyzerRecipe>>
            CRYSTAL_CATALYZER_SERIALIZER =
                    RECIPE_SERIALIZERS.register("crystal_catalyzer", () -> new RecipeSerializer<>(
                            CrystalCatalyzerRecipe.Serializer.CODEC,
                            CrystalCatalyzerRecipe.Serializer.STREAM_CODEC));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LightningStrikeRecipe>>
            LIGHTNING_STRIKE_SERIALIZER =
                    RECIPE_SERIALIZERS.register("lightning_strike", () -> new RecipeSerializer<>(
                            LightningStrikeRecipe.Serializer.CODEC,
                            LightningStrikeRecipe.Serializer.STREAM_CODEC));

    public static final DeferredHolder<RecipeType<?>, RecipeType<LightningStrikeRecipe>> LIGHTNING_STRIKE_TYPE =
            RECIPE_TYPES.register(
                    "lightning_strike",
                    () -> RecipeType.simple(Identifier.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "lightning_strike")));

    public static final DeferredHolder<RecipeType<?>, RecipeType<CrystalCatalyzerRecipe>> CRYSTAL_CATALYZER_TYPE =
            RECIPE_TYPES.register(
                    "crystal_catalyzer",
                    () -> RecipeType.simple(Identifier.fromNamespaceAndPath(
                            AE2LightningTech.MODID,
                            "crystal_catalyzer")));

    private ModRecipeTypes() {
    }
}
