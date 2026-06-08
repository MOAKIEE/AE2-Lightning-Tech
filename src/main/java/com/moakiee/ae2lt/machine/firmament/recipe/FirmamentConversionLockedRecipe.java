package com.moakiee.ae2lt.machine.firmament.recipe;

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

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;

public final class FirmamentConversionLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_PROCESS_TIME = "ProcessTime";
    private static final String TAG_INPUTS = "InputConsumptions";

    private final ResourceLocation recipeId;
    private final ItemStack result;
    private final int processTime;
    private final int[] inputConsumptions;

    public FirmamentConversionLockedRecipe(
            ResourceLocation recipeId,
            ItemStack result,
            int processTime,
            int[] inputConsumptions) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.result = Objects.requireNonNull(result, "result").copy();
        this.processTime = processTime;
        if (result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }
        if (processTime <= 0) {
            throw new IllegalArgumentException("processTime must be positive");
        }
        if (inputConsumptions.length != 3) {
            throw new IllegalArgumentException("inputConsumptions must have length 3");
        }
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public static FirmamentConversionLockedRecipe fromCandidate(FirmamentConversionRecipeCandidate candidate) {
        RecipeHolder<FirmamentConversionRecipe> holder = candidate.recipe();
        return new FirmamentConversionLockedRecipe(
                holder.id(),
                holder.value().getResultStack(),
                holder.value().processTime(),
                candidate.match().inputConsumptions());
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public ItemStack result() {
        return result.copy();
    }

    public int processTime() {
        return processTime;
    }

    public int inputConsumptionForSlot(int slot) {
        if (slot < FirmamentConversionInventory.SLOT_INPUT_0
                || slot > FirmamentConversionInventory.SLOT_INPUT_2) {
            throw new IllegalArgumentException("slot must be one of the three input slots");
        }
        return inputConsumptions[slot];
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.put(TAG_RESULT, result.save(registries, new CompoundTag()));
        tag.putInt(TAG_PROCESS_TIME, processTime);
        tag.put(TAG_INPUTS, new IntArrayTag(Arrays.copyOf(inputConsumptions, inputConsumptions.length)));
        return tag;
    }

    @Nullable
    public static FirmamentConversionLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_RECIPE_ID) || !tag.contains(TAG_RESULT, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack result = ItemStack.parseOptional(registries, tag.getCompound(TAG_RESULT));
        if (result.isEmpty()) {
            return null;
        }

        int processTime = tag.getInt(TAG_PROCESS_TIME);
        int[] inputConsumptions = tag.getIntArray(TAG_INPUTS);
        if (processTime <= 0 || inputConsumptions.length != 3) {
            return null;
        }

        return new FirmamentConversionLockedRecipe(
                ResourceLocation.parse(tag.getString(TAG_RECIPE_ID)),
                result,
                processTime,
                inputConsumptions);
    }
}
