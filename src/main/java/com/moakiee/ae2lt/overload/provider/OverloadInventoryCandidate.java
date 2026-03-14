package com.moakiee.ae2lt.overload.provider;

import java.util.Comparator;
import java.util.Objects;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import appeng.api.stacks.AEKey;

/**
 * One exact inventory candidate visible to the overload provider.
 * <p>
 * Even when an input slot is ID_ONLY, the network is still enumerated in exact
 * entries so the provider can choose concrete stacks to extract.
 */
public record OverloadInventoryCandidate(
        AEKey storageKey,
        ItemStack stack,
        long availableAmount,
        String stableOrderKey
) {
    public static final Comparator<OverloadInventoryCandidate> STABLE_ORDER =
            Comparator.comparing(OverloadInventoryCandidate::stableOrderKey);

    public OverloadInventoryCandidate {
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("stack must not be empty");
        }
        if (availableAmount < 0) {
            throw new IllegalArgumentException("availableAmount must be >= 0");
        }
        Objects.requireNonNull(stableOrderKey, "stableOrderKey");
        stack = stack.copy();
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    public static OverloadInventoryCandidate of(AEKey storageKey, ItemStack stack, long availableAmount) {
        return new OverloadInventoryCandidate(storageKey, stack, availableAmount, defaultStableOrderKey(stack));
    }

    private static String defaultStableOrderKey(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().toString();
        return itemId + "|" + customData;
    }
}
