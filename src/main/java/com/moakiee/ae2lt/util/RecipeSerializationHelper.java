package com.moakiee.ae2lt.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class RecipeSerializationHelper {
    private RecipeSerializationHelper() {
    }

    public static ItemStack itemStackFromJson(JsonObject json) {
        Item item = itemFromJson(json, "id", "item");
        int count = GsonHelper.getAsInt(json, "count", 1);
        if (count <= 0) {
            throw new JsonSyntaxException("Item stack count must be positive");
        }
        return new ItemStack(item, count);
    }

    public static ItemStack itemStackFromJson(JsonObject json, String key) {
        return itemStackFromJson(GsonHelper.getAsJsonObject(json, key));
    }

    public static Block blockFromJson(JsonObject json, String key) {
        return blockFromId(new ResourceLocation(GsonHelper.getAsString(json, key)));
    }

    public static Block blockFromId(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id)
                .orElseThrow(() -> new JsonSyntaxException("Unknown block id: " + id));
    }

    public static FluidStack fluidStackFromJson(JsonObject json) {
        ResourceLocation id = new ResourceLocation(readId(json, "id", "fluid"));
        var fluid = ForgeRegistries.FLUIDS.getValue(id);
        if (fluid == null) {
            throw new JsonSyntaxException("Unknown fluid id: " + id);
        }

        int amount = GsonHelper.getAsInt(json, "amount");
        if (amount <= 0) {
            throw new JsonSyntaxException("Fluid amount must be positive");
        }

        return new FluidStack(fluid, amount);
    }

    public static FluidStack optionalFluidStackFromJson(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return FluidStack.EMPTY;
        }
        return fluidStackFromJson(GsonHelper.getAsJsonObject(json, key));
    }

    public static ResourceLocation resourceLocationFromJson(JsonObject json, String key) {
        return new ResourceLocation(GsonHelper.getAsString(json, key));
    }

    public static ResourceLocation resourceLocationFromJson(JsonElement json) {
        return new ResourceLocation(GsonHelper.convertToString(json, "resource_location"));
    }

    private static Item itemFromJson(JsonObject json, String primaryKey, String fallbackKey) {
        ResourceLocation id = new ResourceLocation(readId(json, primaryKey, fallbackKey));
        return BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new JsonSyntaxException("Unknown item id: " + id));
    }

    private static String readId(JsonObject json, String primaryKey, String fallbackKey) {
        if (json.has(primaryKey)) {
            return GsonHelper.getAsString(json, primaryKey);
        }
        if (fallbackKey != null && json.has(fallbackKey)) {
            return GsonHelper.getAsString(json, fallbackKey);
        }
        throw new JsonSyntaxException("Missing required field '" + primaryKey + "'");
    }
}
