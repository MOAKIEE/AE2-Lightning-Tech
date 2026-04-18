package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

public final class CrystalCatalyzerLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_ENERGY = "Energy";

    private final ResourceLocation recipeId;
    private final ItemStack output;
    private final FluidStack fluid;
    private final int energyPerCycle;

    public CrystalCatalyzerLockedRecipe(
            ResourceLocation recipeId,
            ItemStack output,
            FluidStack fluid,
            int energyPerCycle) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.output = Objects.requireNonNull(output, "output").copy();
        this.fluid = Objects.requireNonNull(fluid, "fluid").copy();
        this.energyPerCycle = energyPerCycle;
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output cannot be empty");
        }
        if (fluid.isEmpty()) {
            throw new IllegalArgumentException("fluid cannot be empty");
        }
        if (energyPerCycle <= 0) {
            throw new IllegalArgumentException("energyPerCycle must be positive");
        }
    }

    public static CrystalCatalyzerLockedRecipe fromCandidate(CrystalCatalyzerRecipeCandidate candidate) {
        RecipeHolder<CrystalCatalyzerRecipe> holder = candidate.recipe();
        CrystalCatalyzerRecipe recipe = holder.value();
        return new CrystalCatalyzerLockedRecipe(
                holder.id(),
                recipe.getOutputTemplate(),
                recipe.fluid(),
                recipe.energyPerCycle());
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public ItemStack output() {
        return output.copy();
    }

    public FluidStack fluid() {
        return fluid.copy();
    }

    public int energyPerCycle() {
        return energyPerCycle;
    }

    public long totalEnergy() {
        return energyPerCycle;
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.put(TAG_OUTPUT, output.save(registries, new CompoundTag()));
        FluidStack.OPTIONAL_CODEC
                .encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), fluid)
                .result()
                .ifPresent(encoded -> tag.put(TAG_FLUID, encoded));
        tag.putInt(TAG_ENERGY, energyPerCycle);
        return tag;
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_RECIPE_ID) || !tag.contains(TAG_OUTPUT, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack output = ItemStack.parseOptional(registries, tag.getCompound(TAG_OUTPUT));
        if (output.isEmpty()) {
            return null;
        }

        FluidStack fluid = FluidStack.OPTIONAL_CODEC
                .parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.get(TAG_FLUID))
                .result()
                .orElse(FluidStack.EMPTY);
        if (fluid.isEmpty()) {
            return null;
        }

        int energy = tag.getInt(TAG_ENERGY);
        if (energy <= 0) {
            return null;
        }

        return new CrystalCatalyzerLockedRecipe(
                ResourceLocation.parse(tag.getString(TAG_RECIPE_ID)),
                output,
                fluid,
                energy);
    }
}
