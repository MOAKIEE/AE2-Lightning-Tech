package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.Arrays;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class OverloadProcessingLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_TOTAL_ENERGY = "TotalEnergy";
    private static final String TAG_TOTAL_LIGHTNING_COST = "TotalLightningCost";
    private static final String TAG_LIGHTNING_TIER = "LightningTier";
    private static final String TAG_PARALLEL = "Parallel";
    private static final String TAG_INPUTS = "InputConsumptions";

    private final Identifier recipeId;
    private final long totalEnergy;
    private final long totalLightningCost;
    private final LightningKey.Tier lightningTier;
    private final int parallel;
    private final int[] inputConsumptions;

    public OverloadProcessingLockedRecipe(
            Identifier recipeId,
            long totalEnergy,
            long totalLightningCost,
            LightningKey.Tier lightningTier,
            int parallel,
            int[] inputConsumptions) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.totalEnergy = totalEnergy;
        this.totalLightningCost = totalLightningCost;
        this.lightningTier = Objects.requireNonNull(lightningTier, "lightningTier");
        int maxParallel = OverloadProcessingFactoryInventory.getMaxParallel();
        if (parallel <= 0 || parallel > maxParallel) {
            throw new IllegalArgumentException("parallel must be in range 1.." + maxParallel);
        }
        if (totalEnergy <= 0L) {
            throw new IllegalArgumentException("totalEnergy must be positive");
        }
        if (totalLightningCost <= 0L) {
            throw new IllegalArgumentException("totalLightningCost must be positive");
        }
        if (inputConsumptions.length != OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("inputConsumptions must have length 9");
        }
        this.parallel = parallel;
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public static OverloadProcessingLockedRecipe fromCandidate(OverloadProcessingRecipeCandidate candidate) {
        RecipeHolder<OverloadProcessingRecipe> holder = candidate.recipe();
        return new OverloadProcessingLockedRecipe(
                holder.id().identifier(),
                candidate.totalEnergy(),
                candidate.totalLightningCost(),
                holder.value().lightningTier(),
                candidate.parallel(),
                candidate.match().inputConsumptions());
    }

    public Identifier recipeId() {
        return recipeId;
    }

    public long totalEnergy() {
        return totalEnergy;
    }

    public long totalLightningCost() {
        return totalLightningCost;
    }

    public LightningKey.Tier lightningTier() {
        return lightningTier;
    }

    public int parallel() {
        return parallel;
    }

    public int inputConsumptionForSlot(int slot) {
        if (slot < OverloadProcessingFactoryInventory.SLOT_INPUT_0
                || slot > OverloadProcessingFactoryInventory.SLOT_INPUT_8) {
            throw new IllegalArgumentException("slot must be an input slot");
        }
        return inputConsumptions[slot];
    }

    public void writeTo(ValueOutput data) {
        data.putString(TAG_RECIPE_ID, recipeId.toString());
        data.putLong(TAG_TOTAL_ENERGY, totalEnergy);
        data.putLong(TAG_TOTAL_LIGHTNING_COST, totalLightningCost);
        data.putString(TAG_LIGHTNING_TIER, lightningTier.getSerializedName());
        data.putInt(TAG_PARALLEL, parallel);
        data.putIntArray(TAG_INPUTS, Arrays.copyOf(inputConsumptions, inputConsumptions.length));
    }

    @Nullable
    public static OverloadProcessingLockedRecipe fromInput(ValueInput data) {
        LightningKey.Tier lightningTier = LightningKey.Tier.fromSerializedName(
                data.getStringOr(
                        TAG_LIGHTNING_TIER,
                        OverloadProcessingRecipe.DEFAULT_LIGHTNING_TIER.getSerializedName()));
        return createOrNull(
                data.getStringOr(TAG_RECIPE_ID, ""),
                data.getLongOr(TAG_TOTAL_ENERGY, 0L),
                data.getLongOr(TAG_TOTAL_LIGHTNING_COST, 0L),
                lightningTier,
                data.getIntOr(TAG_PARALLEL, 0),
                data.getIntArray(TAG_INPUTS).orElse(new int[0]));
    }

    @Nullable
    private static OverloadProcessingLockedRecipe createOrNull(
            String recipeId,
            long totalEnergy,
            long totalLightningCost,
            LightningKey.Tier lightningTier,
            int parallel,
            int[] inputConsumptions) {
        if (recipeId.isEmpty()
                || totalEnergy <= 0L
                || totalLightningCost <= 0L
                || parallel <= 0
                || inputConsumptions.length != OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            return null;
        }

        try {
            return new OverloadProcessingLockedRecipe(
                    Identifier.parse(recipeId),
                    totalEnergy,
                    totalLightningCost,
                    lightningTier,
                    parallel,
                    inputConsumptions);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
