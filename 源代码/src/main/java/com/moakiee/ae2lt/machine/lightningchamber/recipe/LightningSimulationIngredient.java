package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

public record LightningSimulationIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);

    public static final MapCodec<LightningSimulationIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(LightningSimulationIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(LightningSimulationIngredient::count))
            .apply(instance, LightningSimulationIngredient::new));

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeItem(ingredient.getItems()[0]);
        buf.writeVarInt(count);
    }

    public static LightningSimulationIngredient readFromBuf(FriendlyByteBuf buf) {
        return new LightningSimulationIngredient(Ingredient.of(buf.readItem()), buf.readVarInt());
    }

    public LightningSimulationIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
