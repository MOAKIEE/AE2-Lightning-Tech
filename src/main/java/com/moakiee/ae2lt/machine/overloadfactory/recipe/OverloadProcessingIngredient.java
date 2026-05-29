package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.Objects;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record OverloadProcessingIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);

    private static final Codec<Ingredient> INGREDIENT_CODEC = Codec.STRING.xmap(
            s -> Ingredient.fromJson(com.google.gson.JsonParser.parseString(s)),
            i -> i.toJson().toString()
    );

    public static final MapCodec<OverloadProcessingIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    INGREDIENT_CODEC.fieldOf("ingredient").forGetter(OverloadProcessingIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(OverloadProcessingIngredient::count))
            .apply(instance, OverloadProcessingIngredient::new));

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeItem(ingredient.getItems()[0]);
        buf.writeVarInt(count);
    }

    public static OverloadProcessingIngredient readFromBuf(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int count = buf.readVarInt();
        return new OverloadProcessingIngredient(Ingredient.of(stack), count);
    }

    public static OverloadProcessingIngredient fromJson(JsonObject json) {
        Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
        int count = GsonHelper.getAsInt(json, "count", 1);
        return new OverloadProcessingIngredient(ingredient, count);
    }

    public OverloadProcessingIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
