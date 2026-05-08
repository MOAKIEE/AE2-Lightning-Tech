package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Snapshot of the original plain pattern item that was converted into an
 * overload pattern.
 * <p>
 * The stored stack must remain fully decodable by AE2 later, so we persist the
 * complete serialized item stack instead of only custom data.
 */
public final class SourcePatternSnapshot {
    private static final String TAG_ITEM = "Item";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_CUSTOM_DATA = "CustomData";

    private final Identifier itemId;
    @Nullable
    private final CompoundTag serializedStackTag;
    @Nullable
    private final CompoundTag customDataTag;

    public SourcePatternSnapshot(Identifier itemId,
                                 @Nullable CompoundTag serializedStackTag,
                                 @Nullable CompoundTag customDataTag) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.serializedStackTag = serializedStackTag == null ? null : serializedStackTag.copy();
        this.customDataTag = customDataTag == null ? null : customDataTag.copy();
    }

    public static SourcePatternSnapshot fromItemStack(ItemStack stack, HolderLookup.Provider registries) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(registries, "registries");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("source pattern stack must not be empty");
        }

        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var serializedStack = ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack)
                .result()
                .orElseThrow(() -> new IllegalStateException("failed to serialize source pattern stack"));
        if (!(serializedStack instanceof CompoundTag stackTag)) {
            throw new IllegalStateException("serialized source pattern stack was not a compound tag");
        }
        return new SourcePatternSnapshot(itemId, stackTag, null);
    }

    public Identifier itemId() {
        return itemId;
    }

    @Nullable
    public CompoundTag customDataTag() {
        return customDataTag == null ? null : customDataTag.copy();
    }

    /**
     * Recreates an equivalent plain-pattern stack for future reparsing.
     */
    public ItemStack toItemStack(HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "registries");

        if (serializedStackTag != null && !serializedStackTag.isEmpty()) {
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            return ItemStack.OPTIONAL_CODEC.decode(ops, serializedStackTag.copy())
                    .result()
                    .map(com.mojang.datafixers.util.Pair::getFirst)
                    .orElse(ItemStack.EMPTY);
        }

        // Backward compatibility for older overload patterns that only stored
        // item id + custom data.
        var item = BuiltInRegistries.ITEM.getValue(itemId);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        var stack = new ItemStack(item);
        if (customDataTag != null && !customDataTag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customDataTag.copy()));
        }
        return stack;
    }

    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putString(TAG_ITEM, itemId.toString());
        if (serializedStackTag != null && !serializedStackTag.isEmpty()) {
            tag.put(TAG_STACK, serializedStackTag.copy());
        } else if (customDataTag != null && !customDataTag.isEmpty()) {
            tag.put(TAG_CUSTOM_DATA, customDataTag.copy());
        }
        return tag;
    }

    public static SourcePatternSnapshot fromTag(CompoundTag tag) {
        Identifier itemId;
        var itemIdString = tag.getString(TAG_ITEM);
        if (itemIdString.isPresent()) {
            itemId = Identifier.parse(itemIdString.get());
        } else {
            var stackTag = tag.getCompound(TAG_STACK)
                    .orElseThrow(() -> new IllegalArgumentException("source pattern snapshot is missing an item id"));
            itemId = Identifier.parse(stackTag.getString("id")
                    .orElseThrow(() -> new IllegalArgumentException("source pattern stack is missing an item id")));
        }

        CompoundTag serializedStack = tag.getCompound(TAG_STACK)
                .map(CompoundTag::copy)
                .orElse(null);
        CompoundTag customData = tag.getCompound(TAG_CUSTOM_DATA)
                .map(CompoundTag::copy)
                .orElse(null);
        return new SourcePatternSnapshot(itemId, serializedStack, customData);
    }
}
