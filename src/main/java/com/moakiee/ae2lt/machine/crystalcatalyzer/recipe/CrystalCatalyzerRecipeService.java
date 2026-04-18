package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class CrystalCatalyzerRecipeService {
    private CrystalCatalyzerRecipeService() {
    }

    public static Optional<CrystalCatalyzerRecipeCandidate> findRecipe(
            @Nullable Level level,
            CrystalCatalyzerInventory inventory,
            FluidStack fluid) {
        if (level == null) {
            return Optional.empty();
        }

        CrystalCatalyzerRecipeInput input = CrystalCatalyzerRecipeInput.fromMachine(inventory, fluid);
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : getRecipes(level)) {
            if (holder.value().matches(input, level)) {
                return Optional.of(new CrystalCatalyzerRecipeCandidate(holder));
            }
        }
        return Optional.empty();
    }

    public static Optional<CrystalCatalyzerRecipeCandidate> findRecipeById(
            @Nullable Level level,
            net.minecraft.resources.ResourceLocation recipeId) {
        if (level == null) {
            return Optional.empty();
        }
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : getRecipes(level)) {
            if (holder.id().equals(recipeId)) {
                return Optional.of(new CrystalCatalyzerRecipeCandidate(holder));
            }
        }
        return Optional.empty();
    }

    public static boolean isKnownCatalyst(@Nullable Level level, ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : getRecipes(level)) {
            var catalyst = holder.value().catalyst();
            if (catalyst.isPresent() && catalyst.get().test(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isKnownPrimary(@Nullable Level level, ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : getRecipes(level)) {
            if (holder.value().primary().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private static List<RecipeHolder<CrystalCatalyzerRecipe>> getRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get());
    }
}
