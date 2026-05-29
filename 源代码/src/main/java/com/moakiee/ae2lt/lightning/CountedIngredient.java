package com.moakiee.ae2lt.lightning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

public record CountedIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);
    public static final MapCodec<CountedIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(CountedIngredient::count))
            .apply(instance, CountedIngredient::new));

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeItem(ingredient.getItems()[0]);
        buf.writeVarInt(count);
    }

    public static CountedIngredient readFromBuf(FriendlyByteBuf buf) {
        return new CountedIngredient(Ingredient.of(buf.readItem()), buf.readVarInt());
    }

    public CountedIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
