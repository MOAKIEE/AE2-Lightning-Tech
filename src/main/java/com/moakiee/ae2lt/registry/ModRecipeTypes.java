package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, AE2LightningTech.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, AE2LightningTech.MODID);

    public static final RegistryObject<RecipeSerializer<LightningTransformRecipe>> LIGHTNING_TRANSFORM_SERIALIZER =
            RECIPE_SERIALIZERS.register("lightning_transform", LightningTransformRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<LightningTransformRecipe>> LIGHTNING_TRANSFORM_TYPE =
            RECIPE_TYPES.register(
                    "lightning_transform",
                    () -> new RecipeType<LightningTransformRecipe>() {
                        @Override
                        public String toString() {
                            return new ResourceLocation(AE2LightningTech.MODID, "lightning_transform").toString();
                        }
                    });

    public static final RegistryObject<RecipeSerializer<LightningSimulationRecipe>> LIGHTNING_SIMULATION_SERIALIZER =
            RECIPE_SERIALIZERS.register("lightning_simulation", LightningSimulationRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<LightningSimulationRecipe>> LIGHTNING_SIMULATION_TYPE =
            RECIPE_TYPES.register(
                    "lightning_simulation",
                    () -> new RecipeType<LightningSimulationRecipe>() {
                        @Override
                        public String toString() {
                            return new ResourceLocation(AE2LightningTech.MODID, "lightning_simulation").toString();
                        }
                    });

    public static final RegistryObject<RecipeSerializer<LightningAssemblyRecipe>> LIGHTNING_ASSEMBLY_SERIALIZER =
            RECIPE_SERIALIZERS.register("lightning_assembly", LightningAssemblyRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<LightningAssemblyRecipe>> LIGHTNING_ASSEMBLY_TYPE =
            RECIPE_TYPES.register(
                    "lightning_assembly",
                    () -> new RecipeType<LightningAssemblyRecipe>() {
                        @Override
                        public String toString() {
                            return new ResourceLocation(AE2LightningTech.MODID, "lightning_assembly").toString();
                        }
                    });

    public static final RegistryObject<RecipeSerializer<OverloadProcessingRecipe>> OVERLOAD_PROCESSING_SERIALIZER =
            RECIPE_SERIALIZERS.register("overload_processing", OverloadProcessingRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<OverloadProcessingRecipe>> OVERLOAD_PROCESSING_TYPE =
            RECIPE_TYPES.register(
                    "overload_processing",
                    () -> new RecipeType<OverloadProcessingRecipe>() {
                        @Override
                        public String toString() {
                            return new ResourceLocation(AE2LightningTech.MODID, "overload_processing").toString();
                        }
                    });

    public static final RegistryObject<RecipeSerializer<CrystalCatalyzerRecipe>> CRYSTAL_CATALYZER_SERIALIZER =
            RECIPE_SERIALIZERS.register("crystal_catalyzer", CrystalCatalyzerRecipe.Serializer::new);

    public static final RegistryObject<RecipeSerializer<LightningStrikeRecipe>> LIGHTNING_STRIKE_SERIALIZER =
            RECIPE_SERIALIZERS.register("lightning_strike", LightningStrikeRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<LightningStrikeRecipe>> LIGHTNING_STRIKE_TYPE =
            RECIPE_TYPES.register(
                    "lightning_strike",
                    () -> new RecipeType<LightningStrikeRecipe>() {
                        @Override
                        public String toString() {
                            return new ResourceLocation(AE2LightningTech.MODID, "lightning_strike").toString();
                        }
                    });

    public static final RegistryObject<RecipeType<CrystalCatalyzerRecipe>> CRYSTAL_CATALYZER_TYPE =
            RECIPE_TYPES.register(
                    "crystal_catalyzer",
                    () -> new RecipeType<CrystalCatalyzerRecipe>() {
                        @Override
                        public String toString() {
                            return new ResourceLocation(AE2LightningTech.MODID, "crystal_catalyzer").toString();
                        }
                    });

    private ModRecipeTypes() {
    }
}
