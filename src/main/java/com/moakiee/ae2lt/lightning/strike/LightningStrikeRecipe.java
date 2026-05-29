package com.moakiee.ae2lt.lightning.strike;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.moakiee.ae2lt.registry.ModRecipeTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import com.google.gson.JsonObject;
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
    private transient ResourceLocation id;
    private final boolean requiresNaturalLightning;
    private final Block centerInput;
    private final Block centerOutput;
    private final List<StructureRequirement> requirements;

    public LightningStrikeRecipe(ResourceLocation id,
            boolean requiresNaturalLightning,
            Block centerInput,
            Block centerOutput,
            List<StructureRequirement> requirements) {
        this.id = id;
        this.requiresNaturalLightning = requiresNaturalLightning;
        this.centerInput = Objects.requireNonNull(centerInput, "centerInput");
        this.centerOutput = Objects.requireNonNull(centerOutput, "centerOutput");
        this.requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
    }

    // Convenience constructor for Codec usage
    public LightningStrikeRecipe(
            boolean requiresNaturalLightning,
            Block centerInput,
            Block centerOutput,
            List<StructureRequirement> requirements) {
        this(null, requiresNaturalLightning, centerInput, centerOutput, requirements);
    }

    public void setId(ResourceLocation id) {
        this.id = id;
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
    public ItemStack assemble(LightningStrikeRecipeInput input, RegistryAccess registries) {
        return new ItemStack(centerOutput);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return new ItemStack(centerOutput);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public ResourceLocation getId() {
        return id != null ? id : new ResourceLocation("ae2lt", "lightning_strike");
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
        private static final Codec<Block> BLOCK_CODEC = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getCodec();

        private static final MapCodec<LightningStrikeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.BOOL.optionalFieldOf("requires_natural_lightning", false)
                                .forGetter(LightningStrikeRecipe::requiresNaturalLightning),
                        BLOCK_CODEC.fieldOf("center_input").forGetter(LightningStrikeRecipe::centerInput),
                        BLOCK_CODEC.fieldOf("center_output").forGetter(LightningStrikeRecipe::centerOutput),
                        StructureRequirement.CODEC.listOf().fieldOf("requirements")
                                .forGetter(LightningStrikeRecipe::requirements))
                .apply(instance, LightningStrikeRecipe::new));

        @Override
        public LightningStrikeRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            var recipe = CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                    .getOrThrow(false, msg -> {});
            recipe.setId(recipeId);
            return recipe;
        }

        @Override
        public LightningStrikeRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buf) {
            boolean requiresNatural = buf.readBoolean();
            Block centerIn = Block.stateById(buf.readVarInt()).getBlock();
            Block centerOut = Block.stateById(buf.readVarInt()).getBlock();
            int reqCount = buf.readVarInt();
            List<StructureRequirement> reqs = new ArrayList<>(reqCount);
            for (int i = 0; i < reqCount; i++) {
                reqs.add(StructureRequirement.readFromBuf(buf));
            }
            var recipe = new LightningStrikeRecipe(requiresNatural, centerIn, centerOut, reqs);
            recipe.setId(recipeId);
            return recipe;
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, LightningStrikeRecipe recipe) {
            buf.writeBoolean(recipe.requiresNaturalLightning);
            buf.writeVarInt(Block.getId(recipe.centerInput.defaultBlockState()));
            buf.writeVarInt(Block.getId(recipe.centerOutput.defaultBlockState()));
            buf.writeVarInt(recipe.requirements.size());
            for (StructureRequirement req : recipe.requirements) {
                req.writeToBuf(buf);
            }
        }
    }
}
