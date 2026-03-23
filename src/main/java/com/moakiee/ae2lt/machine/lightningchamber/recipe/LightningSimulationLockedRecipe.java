package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.Arrays;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;

public final class LightningSimulationLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_TOTAL_ENERGY = "TotalEnergy";
    private static final String TAG_DUST_COST = "DustCost";
    private static final String TAG_INPUTS = "InputConsumptions";

    private final ResourceLocation recipeId;
    private final ItemStack result;
    private final long totalEnergy;
    private final int overloadDustCost;
    private final int[] inputConsumptions;

    public LightningSimulationLockedRecipe(
            ResourceLocation recipeId,
            ItemStack result,
            long totalEnergy,
            int overloadDustCost,
            int[] inputConsumptions) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.result = Objects.requireNonNull(result, "result").copy();
        this.totalEnergy = totalEnergy;
        this.overloadDustCost = overloadDustCost;
        if (result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }
        if (totalEnergy <= 0) {
            throw new IllegalArgumentException("totalEnergy must be positive");
        }
        if (overloadDustCost <= 0) {
            throw new IllegalArgumentException("overloadDustCost must be positive");
        }
        if (inputConsumptions.length != 3) {
            throw new IllegalArgumentException("inputConsumptions must have length 3");
        }
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public static LightningSimulationLockedRecipe fromCandidate(LightningSimulationRecipeCandidate candidate) {
        RecipeHolder<LightningSimulationRecipe> holder = candidate.recipe();
        return new LightningSimulationLockedRecipe(
                holder.id(),
                holder.value().getResultStack(),
                holder.value().totalEnergy(),
                LightningSimulationRecipeService.REQUIRED_OVERLOAD_DUST,
                candidate.match().inputConsumptions());
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public ItemStack result() {
        return result.copy();
    }

    public long totalEnergy() {
        return totalEnergy;
    }

    public int overloadDustCost() {
        return overloadDustCost;
    }

    public int[] inputConsumptions() {
        return Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public int inputConsumptionForSlot(int slot) {
        if (slot < LightningSimulationChamberInventory.SLOT_INPUT_0
                || slot > LightningSimulationChamberInventory.SLOT_INPUT_2) {
            throw new IllegalArgumentException("slot must be one of the three input slots");
        }
        return inputConsumptions[slot];
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.put(TAG_RESULT, result.save(registries, new CompoundTag()));
        tag.putLong(TAG_TOTAL_ENERGY, totalEnergy);
        tag.putInt(TAG_DUST_COST, overloadDustCost);
        tag.put(TAG_INPUTS, new IntArrayTag(Arrays.copyOf(inputConsumptions, inputConsumptions.length)));
        return tag;
    }

    @Nullable
    public static LightningSimulationLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_RECIPE_ID) || !tag.contains(TAG_RESULT, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack result = ItemStack.parseOptional(registries, tag.getCompound(TAG_RESULT));
        if (result.isEmpty()) {
            return null;
        }

        int[] inputConsumptions = tag.getIntArray(TAG_INPUTS);
        if (inputConsumptions.length != 3) {
            return null;
        }

        long totalEnergy = tag.getLong(TAG_TOTAL_ENERGY);
        int dustCost = tag.getInt(TAG_DUST_COST);
        if (totalEnergy <= 0 || dustCost <= 0) {
            return null;
        }

        return new LightningSimulationLockedRecipe(
                ResourceLocation.parse(tag.getString(TAG_RECIPE_ID)),
                result,
                totalEnergy,
                dustCost,
                inputConsumptions);
    }
}
