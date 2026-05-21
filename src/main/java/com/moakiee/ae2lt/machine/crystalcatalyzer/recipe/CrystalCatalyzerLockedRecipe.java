package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.moakiee.ae2lt.me.key.LightningKey;

public final class CrystalCatalyzerLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_OUTPUT_MULTIPLIER = "OutputMultiplier";
    private static final String TAG_LIGHTNING_COST = "LightningCost";
    private static final String TAG_LIGHTNING_TIER = "LightningTier";

    private final Identifier recipeId;
    private final ItemStack output;
    private final int energyPerCycle;
    private final int outputMultiplier;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;

    public CrystalCatalyzerLockedRecipe(
            Identifier recipeId,
            ItemStack output,
            int energyPerCycle,
            int outputMultiplier,
            int lightningCost,
            LightningKey.Tier lightningTier) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.output = Objects.requireNonNull(output, "output").copy();
        this.energyPerCycle = energyPerCycle;
        this.outputMultiplier = outputMultiplier;
        this.lightningCost = lightningCost;
        this.lightningTier = Objects.requireNonNull(lightningTier, "lightningTier");
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output cannot be empty");
        }
        if (energyPerCycle <= 0) {
            throw new IllegalArgumentException("energyPerCycle must be positive");
        }
        if (outputMultiplier <= 0) {
            throw new IllegalArgumentException("outputMultiplier must be positive");
        }
        if (lightningCost < 1) {
            throw new IllegalArgumentException("lightningCost must be positive");
        }
    }

    public static CrystalCatalyzerLockedRecipe fromCandidate(
            CrystalCatalyzerRecipeCandidate candidate,
            int outputMultiplier) {
        RecipeHolder<CrystalCatalyzerRecipe> holder = candidate.recipe();
        CrystalCatalyzerRecipe recipe = holder.value();
        return new CrystalCatalyzerLockedRecipe(
                holder.id().identifier(),
                recipe.getOutputTemplate(),
                recipe.energyPerCycle(),
                outputMultiplier,
                recipe.lightningCost(),
                recipe.lightningTier());
    }

    public Identifier recipeId() {
        return recipeId;
    }

    public ItemStack output() {
        return output.copy();
    }

    public int energyPerCycle() {
        return energyPerCycle;
    }

    public int outputMultiplier() {
        return outputMultiplier;
    }

    public int lightningCost() {
        return lightningCost;
    }

    public LightningKey.Tier lightningTier() {
        return lightningTier;
    }

    public long totalEnergy() {
        return energyPerCycle;
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        CompoundTag outputTag = new CompoundTag();
        outputTag.store(ItemStack.MAP_CODEC, ops, output);
        tag.put(TAG_OUTPUT, outputTag);
        tag.putInt(TAG_ENERGY, energyPerCycle);
        tag.putInt(TAG_OUTPUT_MULTIPLIER, outputMultiplier);
        tag.putInt(TAG_LIGHTNING_COST, lightningCost);
        tag.putString(TAG_LIGHTNING_TIER, lightningTier.getSerializedName());
        return tag;
    }

    public void writeTo(ValueOutput data) {
        data.putString(TAG_RECIPE_ID, recipeId.toString());
        data.child(TAG_OUTPUT).store(ItemStack.MAP_CODEC, output);
        data.putInt(TAG_ENERGY, energyPerCycle);
        data.putInt(TAG_OUTPUT_MULTIPLIER, outputMultiplier);
        data.putInt(TAG_LIGHTNING_COST, lightningCost);
        data.putString(TAG_LIGHTNING_TIER, lightningTier.getSerializedName());
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        return fromTag(tag, registries, 1);
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(
            CompoundTag tag,
            HolderLookup.Provider registries,
            int defaultOutputMultiplier) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ItemStack output = tag.getCompound(TAG_OUTPUT)
                .flatMap(outputTag -> outputTag.read(ItemStack.MAP_CODEC, ops))
                .orElse(ItemStack.EMPTY);
        if (output.isEmpty()) {
            return null;
        }

        int energy = tag.getIntOr(TAG_ENERGY, 0);
        if (energy <= 0) {
            return null;
        }

        int outputMultiplier = tag.getIntOr(TAG_OUTPUT_MULTIPLIER, defaultOutputMultiplier);
        if (outputMultiplier <= 0) {
            return null;
        }

        int lightningCost = Math.max(1, tag.getIntOr(TAG_LIGHTNING_COST, 1));
        LightningKey.Tier lightningTier = LightningKey.Tier.fromSerializedName(
                tag.getStringOr(TAG_LIGHTNING_TIER, LightningKey.Tier.HIGH_VOLTAGE.getSerializedName()));

        return createOrNull(tag.getStringOr(TAG_RECIPE_ID, ""), output, energy, outputMultiplier,
                lightningCost, lightningTier);
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromInput(ValueInput data, int defaultOutputMultiplier) {
        ItemStack output = data.child(TAG_OUTPUT)
                .flatMap(outputTag -> outputTag.read(ItemStack.MAP_CODEC))
                .orElse(ItemStack.EMPTY);
        if (output.isEmpty()) {
            return null;
        }

        int energy = data.getIntOr(TAG_ENERGY, 0);
        int outputMultiplier = data.getIntOr(TAG_OUTPUT_MULTIPLIER, defaultOutputMultiplier);
        int lightningCost = Math.max(1, data.getIntOr(TAG_LIGHTNING_COST, 1));
        LightningKey.Tier lightningTier = LightningKey.Tier.fromSerializedName(
                data.getStringOr(TAG_LIGHTNING_TIER, LightningKey.Tier.HIGH_VOLTAGE.getSerializedName()));
        return createOrNull(data.getStringOr(TAG_RECIPE_ID, ""), output, energy, outputMultiplier,
                lightningCost, lightningTier);
    }

    @Nullable
    private static CrystalCatalyzerLockedRecipe createOrNull(
            String recipeId,
            ItemStack output,
            int energy,
            int outputMultiplier,
            int lightningCost,
            LightningKey.Tier lightningTier) {
        if (recipeId.isEmpty() || energy <= 0 || outputMultiplier <= 0 || lightningCost < 1) {
            return null;
        }

        try {
            return new CrystalCatalyzerLockedRecipe(
                    Identifier.parse(recipeId),
                    output,
                    energy,
                    outputMultiplier,
                    lightningCost,
                    lightningTier);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
