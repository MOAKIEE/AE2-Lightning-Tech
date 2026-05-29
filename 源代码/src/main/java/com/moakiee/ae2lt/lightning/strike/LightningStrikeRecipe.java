package com.moakiee.ae2lt.lightning.strike;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.moakiee.ae2lt.registry.ModRecipeTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Data-driven multiblock lightning strike recipe.
 *
 * <p>A recipe describes a 3D arrangement of blocks around a "center" block. When
 * a lightning bolt hits the structure and (optionally) was naturally spawned, the
 * center block is transformed into {@link #centerOutput()} and every
 * requirement marked with {@code consume = true} is removed.</p>
 *
 * <p>Matching is performed by {@code NaturalLightningTransformationHandler}
 * via the world; the {@link Recipe} interface is only implemented so the recipe
 * participates in the vanilla datapack and JEI infrastructure.</p>
 */
public final class LightningStrikeRecipe implements Recipe<LightningStrikeRecipeInput> {
    private final boolean requiresNaturalLightning;
    private final Block centerInput;
    private final Block centerOutput;
    private final List<StructureRequirement> requirements;

    public LightningStrikeRecipe(
            boolean requiresNaturalLightning,
            Block centerInput,
            Block centerOutput,
            List<StructureRequirement> requirements) {
        this.requiresNaturalLightning = requiresNaturalLightning;
        this.centerInput = Objects.requireNonNull(centerInput, "centerInput");
        this.centerOutput = Objects.requireNonNull(centerOutput, "centerOutput");
        this.requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
    }

    public boolean requiresNaturalLightning() {
        return requiresNaturalLightning;
    }

    public Block centerInput() {
        return centerInput;
    }

    public Block centerOutput() {
        return centerOutput;
    }

    public List<StructureRequirement> requirements() {
        return requirements;
    }

    @Override
    public boolean matches(LightningStrikeRecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(LightningStrikeRecipeInput input, HolderLookup.Provider registries) {
        return new ItemStack(centerOutput);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(centerOutput);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.LIGHTNING_STRIKE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<LightningStrikeRecipe> {
        private static final Codec<Block> BLOCK_CODEC = BuiltInRegistries.BLOCK.byNameCodec();

        private static final MapCodec<LightningStrikeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.BOOL.optionalFieldOf("requires_natural_lightning", false)
                                .forGetter(LightningStrikeRecipe::requiresNaturalLightning),
                        BLOCK_CODEC.fieldOf("center_input").forGetter(LightningStrikeRecipe::centerInput),
                        BLOCK_CODEC.fieldOf("center_output").forGetter(LightningStrikeRecipe::centerOutput),
                        StructureRequirement.CODEC.listOf().fieldOf("requirements")
                                .forGetter(LightningStrikeRecipe::requirements))
                .apply(instance, LightningStrikeRecipe::new));

        private static void writeToBuf(FriendlyByteBuf buf, LightningStrikeRecipe recipe) {
            buf.writeBoolean(recipe.requiresNaturalLightning);
            buf.writeResourceLocation(BuiltInRegistries.BLOCK.getKey(recipe.centerInput));
            buf.writeResourceLocation(BuiltInRegistries.BLOCK.getKey(recipe.centerOutput));
            buf.writeVarInt(recipe.requirements.size());
            for (StructureRequirement req : recipe.requirements) {
                req.writeToBuf(buf);
            }
        }

        private static LightningStrikeRecipe readFromBuf(FriendlyByteBuf buf) {
            boolean requiresNatural = buf.readBoolean();
            Block centerIn = BuiltInRegistries.BLOCK.by(buf.readResourceLocation());
            Block centerOut = BuiltInRegistries.BLOCK.by(buf.readResourceLocation());
            int size = buf.readVarInt();
            List<StructureRequirement> reqs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                reqs.add(StructureRequirement.readFromBuf(buf));
            }
            return new LightningStrikeRecipe(requiresNatural, centerIn, centerOut, reqs);
        }

        @Override
        public MapCodec<LightningStrikeRecipe> codec() {
            return CODEC;
        }
    }
}
