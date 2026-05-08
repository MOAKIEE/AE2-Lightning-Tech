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

public final class CrystalCatalyzerLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_OUTPUT_MULTIPLIER = "OutputMultiplier";

    private final Identifier recipeId;
    private final ItemStack output;
    private final int energyPerCycle;
    private final int outputMultiplier;

    public CrystalCatalyzerLockedRecipe(
            Identifier recipeId,
            ItemStack output,
            int energyPerCycle,
            int outputMultiplier) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.output = Objects.requireNonNull(output, "output").copy();
        this.energyPerCycle = energyPerCycle;
        this.outputMultiplier = outputMultiplier;
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output cannot be empty");
        }
        if (energyPerCycle <= 0) {
            throw new IllegalArgumentException("energyPerCycle must be positive");
        }
        if (outputMultiplier <= 0) {
            throw new IllegalArgumentException("outputMultiplier must be positive");
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
                outputMultiplier);
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
        return tag;
    }

    public void writeTo(ValueOutput data) {
        data.putString(TAG_RECIPE_ID, recipeId.toString());
        data.child(TAG_OUTPUT).store(ItemStack.MAP_CODEC, output);
        data.putInt(TAG_ENERGY, energyPerCycle);
        data.putInt(TAG_OUTPUT_MULTIPLIER, outputMultiplier);
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

        return createOrNull(tag.getStringOr(TAG_RECIPE_ID, ""), output, energy, outputMultiplier);
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
        return createOrNull(data.getStringOr(TAG_RECIPE_ID, ""), output, energy, outputMultiplier);
    }

    @Nullable
    private static CrystalCatalyzerLockedRecipe createOrNull(
            String recipeId,
            ItemStack output,
            int energy,
            int outputMultiplier) {
        if (recipeId.isEmpty() || energy <= 0 || outputMultiplier <= 0) {
            return null;
        }

        try {
            return new CrystalCatalyzerLockedRecipe(
                    Identifier.parse(recipeId),
                    output,
                    energy,
                    outputMultiplier);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
