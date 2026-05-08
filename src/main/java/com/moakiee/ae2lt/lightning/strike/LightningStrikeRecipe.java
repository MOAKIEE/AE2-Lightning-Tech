package com.moakiee.ae2lt.lightning.strike;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.moakiee.ae2lt.registry.ModRecipeTypes;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
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
    public ItemStack assemble(LightningStrikeRecipeInput input) {
        return new ItemStack(centerOutput);
    }

    public ItemStack getResultItem() {
        return new ItemStack(centerOutput);
    }

    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<LightningStrikeRecipe> getSerializer() {
        return ModRecipeTypes.LIGHTNING_STRIKE_SERIALIZER.get();
    }

    @Override
    public RecipeType<LightningStrikeRecipe> getType() {
        return ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    public static final class Serializer {
        private static final Codec<Block> BLOCK_CODEC = BuiltInRegistries.BLOCK.byNameCodec();

        public static final MapCodec<LightningStrikeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.BOOL.optionalFieldOf("requires_natural_lightning", false)
                                .forGetter(LightningStrikeRecipe::requiresNaturalLightning),
                        BLOCK_CODEC.fieldOf("center_input").forGetter(LightningStrikeRecipe::centerInput),
                        BLOCK_CODEC.fieldOf("center_output").forGetter(LightningStrikeRecipe::centerOutput),
                        StructureRequirement.CODEC.listOf().fieldOf("requirements")
                                .forGetter(LightningStrikeRecipe::requirements))
                .apply(instance, LightningStrikeRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, LightningStrikeRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::encode, Serializer::decode);

        private static void encode(RegistryFriendlyByteBuf buf, LightningStrikeRecipe recipe) {
            ByteBufCodecs.BOOL.encode(buf, recipe.requiresNaturalLightning);
            ByteBufCodecs.registry(net.minecraft.core.registries.Registries.BLOCK).encode(buf, recipe.centerInput);
            ByteBufCodecs.registry(net.minecraft.core.registries.Registries.BLOCK).encode(buf, recipe.centerOutput);
            ByteBufCodecs.collection(java.util.ArrayList::new, StructureRequirement.STREAM_CODEC)
                    .encode(buf, new java.util.ArrayList<>(recipe.requirements));
        }

        private static LightningStrikeRecipe decode(RegistryFriendlyByteBuf buf) {
            boolean requiresNatural = ByteBufCodecs.BOOL.decode(buf);
            Block centerIn = ByteBufCodecs.registry(net.minecraft.core.registries.Registries.BLOCK).decode(buf);
            Block centerOut = ByteBufCodecs.registry(net.minecraft.core.registries.Registries.BLOCK).decode(buf);
            List<StructureRequirement> reqs = ByteBufCodecs.collection(java.util.ArrayList::new, StructureRequirement.STREAM_CODEC)
                    .decode(buf);
            return new LightningStrikeRecipe(requiresNatural, centerIn, centerOut, reqs);
        }
        public MapCodec<LightningStrikeRecipe> codec() {
            return CODEC;
        }
        public StreamCodec<RegistryFriendlyByteBuf, LightningStrikeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
