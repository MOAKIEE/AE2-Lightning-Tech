package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Minimal snapshot of the original plain pattern item that was converted into
 * an overload pattern.
 * <p>
 * This first-pass snapshot preserves item identity and the item's custom-data
 * payload. That is enough for a conversion/editing round-trip without mutating
 * the original plain pattern item.
 */
public final class SourcePatternSnapshot {
    private static final String TAG_ITEM = "Item";
    private static final String TAG_CUSTOM_DATA = "CustomData";

    private final ResourceLocation itemId;
    @Nullable
    private final CompoundTag customDataTag;

    public SourcePatternSnapshot(ResourceLocation itemId, @Nullable CompoundTag customDataTag) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.customDataTag = customDataTag == null ? null : customDataTag.copy();
    }

    public static SourcePatternSnapshot fromItemStack(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("source pattern stack must not be empty");
        }

        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return new SourcePatternSnapshot(itemId, customData.isEmpty() ? null : customData);
    }

    public ResourceLocation itemId() {
        return itemId;
    }

    @Nullable
    public CompoundTag customDataTag() {
        return customDataTag == null ? null : customDataTag.copy();
    }

    /**
     * Recreates an equivalent plain-pattern stack for future reparsing.
     */
    public ItemStack toItemStack() {
        var item = BuiltInRegistries.ITEM.get(itemId);
        var stack = new ItemStack(item);
        if (customDataTag != null && !customDataTag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customDataTag.copy()));
        }
        return stack;
    }

    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putString(TAG_ITEM, itemId.toString());
        if (customDataTag != null && !customDataTag.isEmpty()) {
            tag.put(TAG_CUSTOM_DATA, customDataTag.copy());
        }
        return tag;
    }

    public static SourcePatternSnapshot fromTag(CompoundTag tag) {
        var itemId = ResourceLocation.parse(tag.getString(TAG_ITEM));
        CompoundTag customData = null;
        if (tag.contains(TAG_CUSTOM_DATA, CompoundTag.TAG_COMPOUND)) {
            customData = tag.getCompound(TAG_CUSTOM_DATA).copy();
        }
        return new SourcePatternSnapshot(itemId, customData);
    }
}
