package com.moakiee.ae2lt.machine.lightningassembly.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationIngredient;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class LightningAssemblyRecipe implements Recipe<LightningAssemblyRecipeInput> {
    public static final long MIN_TOTAL_ENERGY = 5L;
    public static final int DEFAULT_LIGHTNING_COST = 4;
    public static final LightningKey.Tier DEFAULT_LIGHTNING_TIER = LightningKey.Tier.HIGH_VOLTAGE;

    private static final Codec<Long> POSITIVE_ENERGY_CODEC = Codec.LONG;
    private static final Codec<Integer> POSITIVE_LIGHTNING_COST_CODEC = Codec.INT;
    private final int priority;
    private final List<LightningSimulationIngredient> inputs;
    private final ItemStack result;
    private final long totalEnergy;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final int totalInputCount;

    public LightningAssemblyRecipe(
            int priority,
            List<LightningSimulationIngredient> inputs,
            ItemStack result,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(lightningTier, "lightningTier");
        if (inputs.isEmpty() || inputs.size() > 9) {
            throw new IllegalArgumentException("inputs must contain 1 to 9 entries");
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }
        if (totalEnergy < MIN_TOTAL_ENERGY) {
            throw new IllegalArgumentException("totalEnergy must be at least " + MIN_TOTAL_ENERGY);
        }
        if (lightningCost <= 0) {
            throw new IllegalArgumentException("lightningCost must be positive");
        }

        this.priority = priority;
        this.inputs = List.copyOf(inputs);
        this.result = result.copy();
        this.totalEnergy = totalEnergy;
        this.lightningCost = lightningCost;
        this.lightningTier = lightningTier;
        this.totalInputCount = this.inputs.stream().mapToInt(LightningSimulationIngredient::count).sum();
    }

    public int priority() {
        return priority;
    }

    public List<LightningSimulationIngredient> inputs() {
        return inputs;
    }

    public ItemStack getResultStack() {
        return result.copy();
    }

    public long totalEnergy() {
        return totalEnergy;
    }

    public int lightningCost() {
        return lightningCost;
    }

    public LightningKey.Tier lightningTier() {
        return lightningTier;
    }

    public int totalInputCount() {
        return totalInputCount;
    }

    @Override
    public boolean matches(LightningAssemblyRecipeInput input, Level level) {
        return planMatch(input).isPresent();
    }

    public Optional<LightningAssemblyRecipeMatch> planMatch(LightningAssemblyRecipeInput input) {
        List<LightningAssemblyRecipeInput.SlotStack> slotStacks = input.slotStacks();
        if (slotStacks.isEmpty() || slotStacks.size() > 9) {
            return Optional.empty();
        }

        int[] slotFlexibility = new int[slotStacks.size()];
        List<List<Integer>> rawMatches = new ArrayList<>(inputs.size());

        for (LightningSimulationIngredient requirement : inputs) {
            List<Integer> matchingSlots = new ArrayList<>();
            int availableCount = 0;

            for (int slotIndex = 0; slotIndex < slotStacks.size(); slotIndex++) {
                var slotStack = slotStacks.get(slotIndex);
                if (!requirement.ingredient().test(slotStack.stack())) {
                    continue;
                }

                matchingSlots.add(slotIndex);
                availableCount += slotStack.stack().getCount();
                slotFlexibility[slotIndex]++;
            }

            if (availableCount < requirement.count()) {
                return Optional.empty();
            }

            rawMatches.add(matchingSlots);
        }

        List<RequirementState> requirements = new ArrayList<>(inputs.size());
        for (int requirementIndex = 0; requirementIndex < inputs.size(); requirementIndex++) {
            LightningSimulationIngredient requirement = inputs.get(requirementIndex);
            List<Integer> matchingSlots = rawMatches.get(requirementIndex);
            matchingSlots.sort(Comparator
                    .comparingInt((Integer slotIndex) -> slotFlexibility[slotIndex])
                    .thenComparing(Comparator.comparingInt(
                            (Integer slotIndex) -> slotStacks.get(slotIndex).stack().getCount()).reversed()));
            requirements.add(new RequirementState(
                    requirement.count(),
                    matchingSlots.stream().mapToInt(Integer::intValue).toArray()));
        }

        requirements.sort(Comparator
                .comparingInt(RequirementState::matchingSlotCount)
                .thenComparing(Comparator.comparingInt(RequirementState::count).reversed()));

        int[] remainingCounts = slotStacks.stream().mapToInt(slotStack -> slotStack.stack().getCount()).toArray();
        int[] slotConsumptions = new int[9];

        if (!allocateRequirement(0, requirements, slotStacks, remainingCounts, slotConsumptions)) {
            return Optional.empty();
        }

        return Optional.of(new LightningAssemblyRecipeMatch(slotConsumptions));
    }

    private ResourceLocation id;

    @Override
    public ResourceLocation getId() {
        return id;
    }

    public void setId(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ItemStack assemble(LightningAssemblyRecipeInput input, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (var input : inputs) {
            ingredients.add(input.ingredient());
        }
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.LIGHTNING_ASSEMBLY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get();
    }

    @Override
    public boolean isIncomplete() {
        return inputs.isEmpty()
                || result.isEmpty()
                || totalEnergy < MIN_TOTAL_ENERGY
                || lightningCost <= 0
                || inputs.stream().anyMatch(input -> input.ingredient().isEmpty());
    }

    private ItemStack rawResult() {
        return result;
    }

    private boolean allocateRequirement(
            int requirementIndex,
            List<RequirementState> requirements,
            List<LightningAssemblyRecipeInput.SlotStack> slotStacks,
            int[] remainingCounts,
            int[] slotConsumptions) {
        if (requirementIndex >= requirements.size()) {
            return true;
        }

        RequirementState requirement = requirements.get(requirementIndex);
        return allocateAcrossSlots(
                requirementIndex,
                requirements,
                requirement,
                slotStacks,
                0,
                requirement.count(),
                remainingCounts,
                slotConsumptions);
    }

    private boolean allocateAcrossSlots(
            int requirementIndex,
            List<RequirementState> requirements,
            RequirementState requirement,
            List<LightningAssemblyRecipeInput.SlotStack> slotStacks,
            int slotCursor,
            int needed,
            int[] remainingCounts,
            int[] slotConsumptions) {
        if (needed == 0) {
            return allocateRequirement(requirementIndex + 1, requirements, slotStacks, remainingCounts, slotConsumptions);
        }
        if (slotCursor >= requirement.matchingSlots.length) {
            return false;
        }
        if (remainingCapacity(requirement.matchingSlots, slotCursor, remainingCounts) < needed) {
            return false;
        }

        int slotIndex = requirement.matchingSlots[slotCursor];
        int maxTake = Math.min(needed, remainingCounts[slotIndex]);
        int machineSlot = slotStacks.get(slotIndex).slot();

        for (int take = maxTake; take >= 0; take--) {
            if (take > 0) {
                remainingCounts[slotIndex] -= take;
                slotConsumptions[machineSlot] += take;
            }

            if (allocateAcrossSlots(
                    requirementIndex,
                    requirements,
                    requirement,
                    slotStacks,
                    slotCursor + 1,
                    needed - take,
                    remainingCounts,
                    slotConsumptions)) {
                return true;
            }

            if (take > 0) {
                slotConsumptions[machineSlot] -= take;
                remainingCounts[slotIndex] += take;
            }
        }

        return false;
    }

    private int remainingCapacity(int[] matchingSlots, int startIndex, int[] remainingCounts) {
        int total = 0;
        for (int index = startIndex; index < matchingSlots.length; index++) {
            total += remainingCounts[matchingSlots[index]];
        }
        return total;
    }

    private static final class RequirementState {
        private final int count;
        private final int[] matchingSlots;

        private RequirementState(int count, int[] matchingSlots) {
            this.count = count;
            this.matchingSlots = matchingSlots;
        }

        private int count() {
            return count;
        }

        private int matchingSlotCount() {
            return matchingSlots.length;
        }
    }

    public static final class Serializer implements RecipeSerializer<LightningAssemblyRecipe> {
        @Override
        public LightningAssemblyRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            int priority = GsonHelper.getAsInt(json, "priority", 0);
            List<LightningSimulationIngredient> inputs = new ArrayList<>();
            for (var element : GsonHelper.getAsJsonArray(json, "inputs")) {
                inputs.add(LightningSimulationIngredient.fromJson(element.getAsJsonObject()));
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            long totalEnergy = GsonHelper.getAsLong(json, "totalEnergy");
            int lightningCost = GsonHelper.getAsInt(json, "lightningCost", DEFAULT_LIGHTNING_COST);
            LightningKey.Tier lightningTier = json.has("lightningTier")
                    ? LightningKey.Tier.valueOf(GsonHelper.getAsString(json, "lightningTier"))
                    : DEFAULT_LIGHTNING_TIER;
            return new LightningAssemblyRecipe(priority, inputs, result, totalEnergy, lightningCost, lightningTier);
        }

        @Override
        public LightningAssemblyRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buf) {
            int priority = buf.readVarInt();
            int inputCount = buf.readVarInt();
            List<LightningSimulationIngredient> inputs = new ArrayList<>(inputCount);
            for (int i = 0; i < inputCount; i++) {
                inputs.add(LightningSimulationIngredient.readFromBuf(buf));
            }
            ItemStack result = buf.readItem();
            long totalEnergy = buf.readVarLong();
            int lightningCost = buf.readVarInt();
            LightningKey.Tier lightningTier = LightningKey.Tier.fromOrdinal(buf.readVarInt());
            return new LightningAssemblyRecipe(priority, inputs, result, totalEnergy, lightningCost, lightningTier);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, LightningAssemblyRecipe recipe) {
            buf.writeVarInt(recipe.priority);
            buf.writeVarInt(recipe.inputs.size());
            for (LightningSimulationIngredient input : recipe.inputs) {
                input.writeToBuf(buf);
            }
            buf.writeItem(recipe.rawResult());
            buf.writeVarLong(recipe.totalEnergy);
            buf.writeVarInt(recipe.lightningCost);
            buf.writeVarInt(recipe.lightningTier.ordinal());
        }
    }
}
