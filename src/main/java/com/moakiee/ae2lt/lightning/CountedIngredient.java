package com.moakiee.ae2lt.lightning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record CountedIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);

    // Custom Ingredient codec for 1.20.1 (Ingredient.CODEC doesn't exist)
    // Uses Ingredient's JSON serialization via Gson
    private static final Codec<Ingredient> INGREDIENT_CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    JsonElement json = JsonParser.parseString(s);
                    // Use reflection or direct call to Ingredient's fromJson
                    Ingredient ing = Ingredient.fromJson(json);
                    return DataResult.success(ing != null ? ing : Ingredient.EMPTY);
                } catch (Exception e) {
                    return DataResult.error(e::getMessage);
                }
            },
            ingredient -> ingredient.toJson().toString()
    );

    public static final MapCodec<CountedIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    INGREDIENT_CODEC.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(CountedIngredient::count))
            .apply(instance, CountedIngredient::new));

    public void writeToBuf(FriendlyByteBuf buf) {
        ItemStack[] items = ingredient.getItems();
        buf.writeItem(items.length > 0 ? items[0] : ItemStack.EMPTY);
        buf.writeVarInt(count);
    }

    public static CountedIngredient readFromBuf(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int count = buf.readVarInt();
        Ingredient ing = Ingredient.of(stack);
        return new CountedIngredient(ing, count);
    }

    public CountedIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
