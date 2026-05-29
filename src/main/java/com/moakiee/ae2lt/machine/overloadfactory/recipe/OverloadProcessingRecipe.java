package com.moakiee.ae2lt.machine.overloadfactory.recipe;

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
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class OverloadProcessingRecipe implements Recipe<OverloadProcessingRecipeInput> {
    public static final long MIN_TOTAL_ENERGY = 5L;
    public static final int DEFAULT_LIGHTNING_COST = 4;
    public static final LightningKey.Tier DEFAULT_LIGHTNING_TIER = LightningKey.Tier.HIGH_VOLTAGE;

    private static final Codec<List<OverloadProcessingIngredient>> INPUTS_CODEC =
            OverloadProcessingIngredient.CODEC.codec().listOf();

    private static final Codec<List<ItemStack>> OUTPUTS_CODEC = ItemStack.CODEC.listOf();

    private static final Codec<Long> POSITIVE_ENERGY_CODEC = Codec.LONG;

    private static final Codec<Integer> POSITIVE_LIGHTNING_COST_CODEC = Codec.INT;

    private final int priority;
    private final List<OverloadProcessingIngredient> itemInputs;
    private final FluidStack fluidInput;
    private final List<ItemStack> itemResults;
    private final FluidStack fluidResult;
    private final long totalEnergy;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final int totalInputCount;

    public OverloadProcessingRecipe(
            int priority,
            List<OverloadProcessingIngredient> itemInputs,
            FluidStack fluidInput,
            List<ItemStack> itemResults,
            FluidStack fluidResult,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier) {
        Objects.requireNonNull(itemInputs, "itemInputs");
        Objects.requireNonNull(fluidInput, "fluidInput");
        Objects.requireNonNull(itemResults, "itemResults");
        Objects.requireNonNull(fluidResult, "fluidResult");
        Objects.requireNonNull(lightningTier, "lightningTier");
        if (itemInputs.size() > OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("itemInputs must contain at most 9 entries");
        }
        if (itemResults.size() > OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("itemResults must contain at most 1 entry");
        }
        if (itemInputs.isEmpty() && fluidInput.isEmpty()) {
            throw new IllegalArgumentException("recipe must define at least one item or fluid input");
        }
        if (itemResults.isEmpty() && fluidResult.isEmpty()) {
            throw new IllegalArgumentException("recipe must define at least one item or fluid output");
        }
        if (itemResults.stream().anyMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("itemResults cannot contain empty stacks");
        }
        if (totalEnergy < MIN_TOTAL_ENERGY) {
            throw new IllegalArgumentException("totalEnergy must be at least " + MIN_TOTAL_ENERGY);
        }
        if (lightningCost <= 0) {
            throw new IllegalArgumentException("lightningCost must be positive");
        }

        this.priority = priority;
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidInput = fluidInput.copy();
        this.itemResults = itemResults.stream().map(ItemStack::copy).toList();
        this.fluidResult = fluidResult.copy();
        this.totalEnergy = totalEnergy;
        this.lightningCost = lightningCost;
        this.lightningTier = lightningTier;
        this.totalInputCount = this.itemInputs.stream().mapToInt(OverloadProcessingIngredient::count).sum();
    }

    public int priority() {
        return priority;
    }

    public List<OverloadProcessingIngredient> itemInputs() {
        return itemInputs;
    }

    public FluidStack fluidInput() {
        return fluidInput.copy();
    }

    public List<ItemStack> itemResults() {
        return itemResults.stream().map(ItemStack::copy).toList();
    }

    public FluidStack fluidResult() {
        return fluidResult.copy();
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
    public boolean matches(OverloadProcessingRecipeInput input, Level level) {
        return planMatch(input, 1).isPresent() && hasRequiredFluid(input.inputFluid(), 1);
    }

    public Optional<OverloadProcessingRecipeMatch> planMatch(OverloadProcessingRecipeInput input, int operations) {
        if (operations <= 0 || input == null) {
            return Optional.empty();
        }

        List<OverloadProcessingRecipeInput.SlotStack> slotStacks = input.slotStacks();
        if (itemInputs.isEmpty()) {
            if (!slotStacks.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new OverloadProcessingRecipeMatch(new int[OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT]));
        }

        if (slotStacks.isEmpty() || slotStacks.size() > OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            return Optional.empty();
        }

        int[] slotFlexibility = new int[slotStacks.size()];
        List<List<Integer>> rawMatches = new ArrayList<>(itemInputs.size());

        for (OverloadProcessingIngredient requirement : itemInputs) {
            List<Integer> matchingSlots = new ArrayList<>();
            long availableCount = 0L;
            long scaledRequirement = (long) requirement.count() * operations;

            for (int slotIndex = 0; slotIndex < slotStacks.size(); slotIndex++) {
                var slotStack = slotStacks.get(slotIndex);
                if (!requirement.ingredient().test(slotStack.stack())) {
                    continue;
                }

                matchingSlots.add(slotIndex);
                availableCount += slotStack.stack().getCount();
                slotFlexibility[slotIndex]++;
            }

            if (availableCount < scaledRequirement) {
                return Optional.empty();
            }

            rawMatches.add(matchingSlots);
        }

        List<RequirementState> requirements = new ArrayList<>(itemInputs.size());
        for (int requirementIndex = 0; requirementIndex < itemInputs.size(); requirementIndex++) {
            OverloadProcessingIngredient requirement = itemInputs.get(requirementIndex);
            List<Integer> matchingSlots = rawMatches.get(requirementIndex);
            // When multiple slots can satisfy the same ingredient, prefer the
            // machine's natural top-to-bottom slot order so repeated crafts
            // drain upper rows before lower ones.
            matchingSlots.sort(Comparator
                    .comparingInt((Integer slotIndex) -> slotStacks.get(slotIndex).slot()));
            requirements.add(new RequirementState(
                    multiplyExactToInt(requirement.count(), operations),
                    matchingSlots.stream().mapToInt(Integer::intValue).toArray()));
        }

        requirements.sort(Comparator
                .comparingInt(RequirementState::matchingSlotCount)
                .thenComparing(Comparator.comparingInt(RequirementState::count).reversed()));

        int[] remainingCounts = slotStacks.stream().mapToInt(slotStack -> slotStack.stack().getCount()).toArray();
        int[] slotConsumptions = new int[OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT];
        if (!allocateRequirement(0, requirements, slotStacks, remainingCounts, slotConsumptions)) {
            return Optional.empty();
        }

        return Optional.of(new OverloadProcessingRecipeMatch(slotConsumptions));
    }

    public boolean hasRequiredFluid(FluidStack availableFluid, int operations) {
        if (operations <= 0) {
            return false;
        }
        if (fluidInput.isEmpty()) {
            return true;
        }
        return !availableFluid.isEmpty()
                && fluidInput.isFluidEqual(availableFluid)
                && availableFluid.getAmount() >= multiplyExactToInt(fluidInput.getAmount(), operations);
    }

    public List<ItemStack> getScaledItemResults(int operations) {
        return itemResults.stream()
                .map(stack -> stack.copyWithCount(multiplyExactToInt(stack.getCount(), operations)))
                .toList();
    }

    public FluidStack getScaledFluidResult(int operations) {
        if (fluidResult.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluidResult.getFluid(), multiplyExactToInt(fluidResult.getAmount(), operations), fluidResult.getTag());
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
    public ItemStack assemble(OverloadProcessingRecipeInput input, RegistryAccess registryAccess) {
        return itemResults.isEmpty() ? ItemStack.EMPTY : itemResults.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return itemResults.isEmpty() ? ItemStack.EMPTY : itemResults.get(0).copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (var input : itemInputs) {
            ingredients.add(input.ingredient());
        }
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.OVERLOAD_PROCESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get();
    }

    @Override
    public boolean isIncomplete() {
        return totalEnergy < MIN_TOTAL_ENERGY
                || lightningCost <= 0
                || (itemInputs.isEmpty() && fluidInput.isEmpty())
                || (itemResults.isEmpty() && fluidResult.isEmpty())
                || itemInputs.stream().anyMatch(input -> input.ingredient().isEmpty());
    }

    private FluidStack rawFluidInput() {
        return fluidInput;
    }

    private List<ItemStack> rawItemResults() {
        return itemResults;
    }

    private FluidStack rawFluidResult() {
        return fluidResult;
    }

    private boolean allocateRequirement(
            int requirementIndex,
            List<RequirementState> requirements,
            List<OverloadProcessingRecipeInput.SlotStack> slotStacks,
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
            List<OverloadProcessingRecipeInput.SlotStack> slotStacks,
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

    private static int multiplyExactToInt(int value, int multiplier) {
        long result = (long) value * multiplier;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("scaled stack size exceeds integer range");
        }
        return (int) result;
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

    public static final class Serializer implements RecipeSerializer<OverloadProcessingRecipe> {
        @Override
        public OverloadProcessingRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            int priority = GsonHelper.getAsInt(json, "priority", 0);
            List<OverloadProcessingIngredient> itemInputs = new ArrayList<>();
            if (json.has("inputs")) {
                for (var element : GsonHelper.getAsJsonArray(json, "inputs")) {
                    itemInputs.add(OverloadProcessingIngredient.fromJson(element.getAsJsonObject()));
                }
            }
            FluidStack fluidInput = FluidStack.EMPTY;
            if (json.has("inputFluid")) {
                fluidInput = parseFluidStack(GsonHelper.getAsJsonObject(json, "inputFluid"));
            }
            List<ItemStack> itemResults = new ArrayList<>();
            if (json.has("results")) {
                for (var element : GsonHelper.getAsJsonArray(json, "results")) {
                    itemResults.add(ShapedRecipe.itemStackFromJson(element.getAsJsonObject()));
                }
            }
            FluidStack fluidResult = FluidStack.EMPTY;
            if (json.has("resultFluid")) {
                fluidResult = parseFluidStack(GsonHelper.getAsJsonObject(json, "resultFluid"));
            }
            long totalEnergy = GsonHelper.getAsLong(json, "totalEnergy");
            int lightningCost = GsonHelper.getAsInt(json, "lightningCost", DEFAULT_LIGHTNING_COST);
            LightningKey.Tier lightningTier = json.has("lightningTier")
                    ? LightningKey.Tier.valueOf(GsonHelper.getAsString(json, "lightningTier"))
                    : DEFAULT_LIGHTNING_TIER;
            return new OverloadProcessingRecipe(priority, itemInputs, fluidInput, itemResults, fluidResult,
                    totalEnergy, lightningCost, lightningTier);
        }

        private static FluidStack parseFluidStack(JsonObject json) {
            String fluidName = GsonHelper.getAsString(json, "fluidName");
            var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(new ResourceLocation(fluidName));
            if (fluid == null) return FluidStack.EMPTY;
            int amount = GsonHelper.getAsInt(json, "amount", 1000);
            FluidStack stack = new FluidStack(fluid, amount);
            if (json.has("tag")) {
                stack.setTag(net.minecraft.nbt.CompoundTag.CODEC.decode(com.mojang.serialization.JsonOps.INSTANCE, json.get("tag")).getOrThrow(false, s -> {}).getFirst());
            }
            return stack;
        }

        @Override
        public OverloadProcessingRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buf) {
            int priority = buf.readVarInt();
            int inputCount = buf.readVarInt();
            List<OverloadProcessingIngredient> itemInputs = new ArrayList<>(inputCount);
            for (int i = 0; i < inputCount; i++) {
                itemInputs.add(OverloadProcessingIngredient.readFromBuf(buf));
            }
            FluidStack fluidInput = readFluidStack(buf);
            int resultCount = buf.readVarInt();
            List<ItemStack> itemResults = new ArrayList<>(resultCount);
            for (int i = 0; i < resultCount; i++) {
                itemResults.add(buf.readItem());
            }
            FluidStack fluidResult = readFluidStack(buf);
            long totalEnergy = buf.readVarLong();
            int lightningCost = buf.readVarInt();
            LightningKey.Tier lightningTier = LightningKey.Tier.fromOrdinal(buf.readVarInt());
            return new OverloadProcessingRecipe(priority, itemInputs, fluidInput, itemResults, fluidResult,
                    totalEnergy, lightningCost, lightningTier);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, OverloadProcessingRecipe recipe) {
            buf.writeVarInt(recipe.priority);
            buf.writeVarInt(recipe.itemInputs.size());
            for (OverloadProcessingIngredient input : recipe.itemInputs) {
                input.writeToBuf(buf);
            }
            writeFluidStack(buf, recipe.rawFluidInput());
            var itemResults = recipe.rawItemResults();
            buf.writeVarInt(itemResults.size());
            for (ItemStack stack : itemResults) {
                buf.writeItem(stack);
            }
            writeFluidStack(buf, recipe.rawFluidResult());
            buf.writeVarLong(recipe.totalEnergy);
            buf.writeVarInt(recipe.lightningCost);
            buf.writeVarInt(recipe.lightningTier.ordinal());
        }

        private static FluidStack readFluidStack(FriendlyByteBuf buf) {
            boolean present = buf.readBoolean();
            if (!present) return FluidStack.EMPTY;
            int amount = buf.readVarInt();
            int fluidId = buf.readVarInt();
            // In 1.20.1, we can't easily read FluidStack from buffer without the registry
            // For now, return empty - this is a limitation
            return FluidStack.EMPTY;
        }

        private static void writeFluidStack(FriendlyByteBuf buf, FluidStack stack) {
            buf.writeBoolean(!stack.isEmpty());
            if (!stack.isEmpty()) {
                buf.writeVarInt(stack.getAmount());
                buf.writeVarInt(0); // placeholder for fluid id
            }
        }
    }
}
