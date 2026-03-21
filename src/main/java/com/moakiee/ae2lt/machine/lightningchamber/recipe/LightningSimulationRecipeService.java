package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class LightningSimulationRecipeService {
    public static final int REQUIRED_OVERLOAD_DUST = 16;

    private static final Comparator<RecipeHolder<LightningSimulationRecipe>> RECIPE_ORDER = Comparator
            .<RecipeHolder<LightningSimulationRecipe>>comparingInt(holder -> holder.value().priority())
            .reversed()
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<LightningSimulationRecipe> holder) -> holder.value().inputs().size()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<LightningSimulationRecipe> holder) -> holder.value().totalInputCount()).reversed())
            .thenComparing(holder -> holder.id().toString());

    private LightningSimulationRecipeService() {
    }

    public static Optional<LightningSimulationRecipeCandidate> findFirstProcessable(
            Level level,
            LightningSimulationChamberInventory inventory) {
        if (level == null) {
            return Optional.empty();
        }
        if (!hasRequiredOverloadDust(inventory)) {
            return Optional.empty();
        }

        LightningSimulationRecipeInput input = LightningSimulationRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        List<RecipeHolder<LightningSimulationRecipe>> recipes =
                new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get()));
        recipes.sort(RECIPE_ORDER);

        for (RecipeHolder<LightningSimulationRecipe> recipe : recipes) {
            Optional<LightningSimulationRecipeMatch> match = recipe.value().planMatch(input);
            if (match.isEmpty()) {
                continue;
            }

            if (!canAcceptOutput(inventory, recipe.value().getResultStack())) {
                continue;
            }

            return Optional.of(new LightningSimulationRecipeCandidate(recipe, match.get()));
        }

        return Optional.empty();
    }

    public static Optional<RecipeHolder<LightningSimulationRecipe>> findRecipeById(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }

        for (RecipeHolder<LightningSimulationRecipe> recipe
                : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())) {
            if (recipe.id().equals(recipeId)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
    }

    public static Optional<LightningSimulationRecipeCandidate> findLockedRecipeMatch(
            Level level,
            LightningSimulationChamberInventory inventory,
            LightningSimulationLockedRecipe lockedRecipe) {
        if (level == null || lockedRecipe == null || !hasRequiredOverloadDust(inventory)) {
            return Optional.empty();
        }

        Optional<RecipeHolder<LightningSimulationRecipe>> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        LightningSimulationRecipeInput input = LightningSimulationRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<LightningSimulationRecipeMatch> match = recipe.get().value().planMatch(input);
        if (match.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LightningSimulationRecipeCandidate(recipe.get(), match.get()));
    }

    public static boolean hasRequiredOverloadDust(LightningSimulationChamberInventory inventory) {
        ItemStack overloadDust = inventory.getStackInSlot(LightningSimulationChamberInventory.SLOT_OVERLOAD_DUST);
        return !overloadDust.isEmpty() && overloadDust.getCount() >= REQUIRED_OVERLOAD_DUST;
    }

    public static boolean canAcceptOutput(LightningSimulationChamberInventory inventory, ItemStack result) {
        return inventory.canAcceptRecipeOutput(result);
    }
}
