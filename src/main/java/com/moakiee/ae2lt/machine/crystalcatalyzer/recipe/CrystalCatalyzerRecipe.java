package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import com.moakiee.ae2lt.registry.ModRecipeTypes;

/**
 * Crystal catalyzer recipe.
 *
 * <p>Fields:</p>
 * <ul>
 *     <li>{@code catalyst}: optional. When absent, the catalyst slot must be empty;
 *         when present, the slot content must match the ingredient and have at least
 *         {@code catalystCount} items (the stack is <em>not</em> consumed).</li>
 *     <li>{@code fluid}: mandatory; the tank must contain this fluid with at least {@code fluid.amount}.</li>
 *     <li>{@code output}: base per-cycle item output (final count = {@code output.count} × matrix multiplier).</li>
 *     <li>{@code energyPerCycle}: total energy (AE) consumed per cycle.</li>
 * </ul>
 */
public final class CrystalCatalyzerRecipe implements Recipe<CrystalCatalyzerRecipeInput> {
    public static final int MIN_ENERGY_PER_CYCLE = 1;

    private static final Codec<Integer> POSITIVE_ENERGY_CODEC = Codec.INT.validate(energy -> {
        if (energy < MIN_ENERGY_PER_CYCLE) {
            return DataResult.error(() -> "energyPerCycle must be at least " + MIN_ENERGY_PER_CYCLE);
        }
        return DataResult.success(energy);
    });

    private static final Codec<Integer> NON_NEGATIVE_COUNT_CODEC = Codec.INT.validate(count -> {
        if (count < 0) {
            return DataResult.error(() -> "count must be non-negative");
        }
        return DataResult.success(count);
    });

    private static final Codec<FluidStack> FLUID_CODEC = FluidStack.CODEC.validate(stack -> {
        if (stack.isEmpty()) {
            return DataResult.error(() -> "fluid cannot be empty");
        }
        return DataResult.success(stack);
    });

    private final Optional<Ingredient> catalyst;
    private final int catalystCount;
    private final FluidStack fluid;
    private final ItemStack output;
    private final int energyPerCycle;

    public CrystalCatalyzerRecipe(
            Optional<Ingredient> catalyst,
            int catalystCount,
            FluidStack fluid,
            ItemStack output,
            int energyPerCycle) {
        this.catalyst = Objects.requireNonNull(catalyst, "catalyst");
        this.catalystCount = catalystCount;
        this.fluid = Objects.requireNonNull(fluid, "fluid").copy();
        this.output = Objects.requireNonNull(output, "output").copy();
        this.energyPerCycle = energyPerCycle;
        if (catalyst.isPresent() && catalystCount <= 0) {
            throw new IllegalArgumentException("catalystCount must be positive when catalyst is present");
        }
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output cannot be empty");
        }
        if (energyPerCycle < MIN_ENERGY_PER_CYCLE) {
            throw new IllegalArgumentException("energyPerCycle must be at least " + MIN_ENERGY_PER_CYCLE);
        }
    }

    public Optional<Ingredient> catalyst() {
        return catalyst;
    }

    public int catalystCount() {
        return catalystCount;
    }

    public FluidStack fluid() {
        return fluid.copy();
    }

    public ItemStack getOutputTemplate() {
        return output.copy();
    }

    public int energyPerCycle() {
        return energyPerCycle;
    }

    public boolean catalystMatches(ItemStack stack) {
        if (catalyst.isEmpty()) {
            return stack.isEmpty();
        }
        if (stack.isEmpty() || !catalyst.get().test(stack)) {
            return false;
        }
        return stack.getCount() >= catalystCount;
    }

    public boolean fluidMatches(FluidStack tankFluid) {
        if (tankFluid.isEmpty()) {
            return false;
        }
        return FluidStack.isSameFluidSameComponents(fluid, tankFluid)
                && tankFluid.getAmount() >= fluid.getAmount();
    }

    @Override
    public boolean matches(CrystalCatalyzerRecipeInput input, Level level) {
        return catalystMatches(input.catalyst()) && fluidMatches(input.fluid());
    }

    @Override
    public ItemStack assemble(CrystalCatalyzerRecipeInput input, HolderLookup.Provider registries) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return output.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        catalyst.ifPresent(list::add);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.CRYSTAL_CATALYZER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get();
    }

    @Override
    public boolean isIncomplete() {
        return output.isEmpty()
                || fluid.isEmpty()
                || energyPerCycle < MIN_ENERGY_PER_CYCLE
                || (catalyst.isPresent() && catalystCount <= 0);
    }

    private ItemStack rawOutput() {
        return output;
    }

    private FluidStack rawFluid() {
        return fluid;
    }

    public static final class Serializer implements RecipeSerializer<CrystalCatalyzerRecipe> {
        private static final MapCodec<CrystalCatalyzerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.optionalFieldOf("catalyst").forGetter(CrystalCatalyzerRecipe::catalyst),
                        NON_NEGATIVE_COUNT_CODEC.optionalFieldOf("catalystCount", 0).forGetter(CrystalCatalyzerRecipe::catalystCount),
                        FLUID_CODEC.fieldOf("fluid").forGetter(CrystalCatalyzerRecipe::rawFluid),
                        ItemStack.STRICT_CODEC.fieldOf("output").forGetter(CrystalCatalyzerRecipe::rawOutput),
                        POSITIVE_ENERGY_CODEC.fieldOf("energyPerCycle").forGetter(CrystalCatalyzerRecipe::energyPerCycle))
                .apply(instance, CrystalCatalyzerRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> OPTIONAL_INGREDIENT_STREAM_CODEC =
                ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC);

        private static final StreamCodec<RegistryFriendlyByteBuf, CrystalCatalyzerRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::encode, Serializer::decode);

        private static void encode(RegistryFriendlyByteBuf buf, CrystalCatalyzerRecipe recipe) {
            OPTIONAL_INGREDIENT_STREAM_CODEC.encode(buf, recipe.catalyst);
            ByteBufCodecs.VAR_INT.encode(buf, recipe.catalystCount);
            FluidStack.STREAM_CODEC.encode(buf, recipe.fluid);
            ItemStack.STREAM_CODEC.encode(buf, recipe.output);
            ByteBufCodecs.VAR_INT.encode(buf, recipe.energyPerCycle);
        }

        private static CrystalCatalyzerRecipe decode(RegistryFriendlyByteBuf buf) {
            Optional<Ingredient> catalyst = OPTIONAL_INGREDIENT_STREAM_CODEC.decode(buf);
            int catalystCount = ByteBufCodecs.VAR_INT.decode(buf);
            FluidStack fluid = FluidStack.STREAM_CODEC.decode(buf);
            ItemStack output = ItemStack.STREAM_CODEC.decode(buf);
            int energyPerCycle = ByteBufCodecs.VAR_INT.decode(buf);
            return new CrystalCatalyzerRecipe(catalyst, catalystCount, fluid, output, energyPerCycle);
        }

        @Override
        public MapCodec<CrystalCatalyzerRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CrystalCatalyzerRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
