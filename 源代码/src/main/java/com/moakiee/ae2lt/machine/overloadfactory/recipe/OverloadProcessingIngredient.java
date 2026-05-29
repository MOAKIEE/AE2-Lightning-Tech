package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

public record OverloadProcessingIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);

    public static final MapCodec<OverloadProcessingIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(OverloadProcessingIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(OverloadProcessingIngredient::count))
            .apply(instance, OverloadProcessingIngredient::new));

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeItem(ingredient.getItems()[0]);
        buf.writeVarInt(count);
    }

    public static OverloadProcessingIngredient readFromBuf(FriendlyByteBuf buf) {
        return new OverloadProcessingIngredient(Ingredient.of(buf.readItem()), buf.readVarInt());
    }

    public OverloadProcessingIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
