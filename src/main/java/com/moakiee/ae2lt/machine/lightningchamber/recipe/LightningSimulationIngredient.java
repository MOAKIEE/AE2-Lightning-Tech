package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.Objects;

import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record LightningSimulationIngredient(Ingredient ingredient, int count) {

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeItem(ingredient.getItems()[0]);
        buf.writeVarInt(count);
    }

    public static LightningSimulationIngredient readFromBuf(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int count = buf.readVarInt();
        return new LightningSimulationIngredient(Ingredient.of(stack), count);
    }

    public static LightningSimulationIngredient fromJson(JsonObject json) {
        Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
        int count = GsonHelper.getAsInt(json, "count", 1);
        return new LightningSimulationIngredient(ingredient, count);
    }

    public LightningSimulationIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
