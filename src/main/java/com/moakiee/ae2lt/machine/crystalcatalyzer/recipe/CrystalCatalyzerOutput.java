package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Iterator;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 水晶催化器配方的产物声明。
 *
 * <p>支持两种 JSON 写法：</p>
 * <ul>
 *     <li>{@code {"id": "modid:item", "count": N}} —— 直接指定一个物品。</li>
 *     <li>{@code {"tag": "modid:path", "count": N}} —— 引用一个物品 tag，运行时解析为该 tag 内的第一个物品。
 *         若 tag 为空（即装包内没有任何物品被 tag 命中），配方等同于"输出为空"，会被机器和 JEI 跳过。</li>
 * </ul>
 *
 * <p>采用 tag 形式可以让多模组装包里"任何一个 amethyst 粉物品"都能被识别，无需为每个潜在模组写一份配方。</p>
 */
public interface CrystalCatalyzerOutput {

    Codec<CrystalCatalyzerOutput> CODEC = Codec.either(OfTag.CODEC, ItemStack.CODEC)
            .xmap(
                    either -> either.map(tag -> (CrystalCatalyzerOutput) tag, OfItem::new),
                    output -> output instanceof OfTag
                            ? Either.left((OfTag) output)
                            : Either.right(((OfItem) output).stack()));

    /**
     * Resolve to a concrete {@link ItemStack}. Returns {@link ItemStack#EMPTY} when this is a tag
     * output and the tag is empty in the current registry/datapack state.
     */
    ItemStack resolve();

    int count();

    static CrystalCatalyzerOutput ofItem(ItemStack stack) {
        return new OfItem(stack.copy());
    }

    static CrystalCatalyzerOutput ofTag(TagKey<Item> tag, int count) {
        return new OfTag(tag, count);
    }

    static void writeToBuf(FriendlyByteBuf buf, CrystalCatalyzerOutput output) {
        if (output instanceof OfItem) {
            buf.writeBoolean(false);
            buf.writeItem(((OfItem) output).stack());
        } else if (output instanceof OfTag) {
            OfTag tag = (OfTag) output;
            buf.writeBoolean(true);
            buf.writeResourceLocation(tag.tag().location());
            buf.writeVarInt(tag.count());
        }
    }

    static CrystalCatalyzerOutput readFromBuf(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            ResourceLocation tagId = buf.readResourceLocation();
            int count = buf.readVarInt();
            return new OfTag(TagKey.create(Registries.ITEM, tagId), count);
        }
        ItemStack stack = buf.readItem();
        return new OfItem(stack);
    }

    final class OfItem implements CrystalCatalyzerOutput {
        private final ItemStack stack;

        public OfItem(ItemStack stack) {
            stack = stack.copy();
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("output stack cannot be empty");
            }
            this.stack = stack;
        }

        public ItemStack stack() {
            return stack;
        }

        @Override
        public ItemStack resolve() {
            return stack.copy();
        }

        @Override
        public int count() {
            return stack.getCount();
        }
    }

    final class OfTag implements CrystalCatalyzerOutput {
        public static final Codec<OfTag> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(OfTag::tag),
                        Codec.INT.optionalFieldOf("count", 1).forGetter(OfTag::count))
                .apply(instance, OfTag::new));

        private final TagKey<Item> tag;
        private final int count;

        public OfTag(TagKey<Item> tag, int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("tag output count must be positive");
            }
            this.tag = tag;
            this.count = count;
        }

        public TagKey<Item> tag() {
            return tag;
        }

        public int count() {
            return count;
        }

        @Override
        public ItemStack resolve() {
            var tagManager = ForgeRegistries.ITEMS.tags();
            if (tagManager == null) {
                return ItemStack.EMPTY;
            }
            var holders = tagManager.getTag(tag);
            if (holders == null || holders.isEmpty()) {
                return ItemStack.EMPTY;
            }
            var iterator = holders.iterator();
            if (!iterator.hasNext()) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(iterator.next(), count);
        }
    }
}