package com.moakiee.ae2lt.lightning.strike;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;

public record StructureRequirement(BlockPos offset, Block block, boolean consume) {
    public static final Codec<StructureRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("offset").forGetter(StructureRequirement::offset),
                    BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(StructureRequirement::block),
                    Codec.BOOL.optionalFieldOf("consume", false).forGetter(StructureRequirement::consume))
            .apply(instance, StructureRequirement::new));

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeBlockPos(offset);
        buf.writeResourceLocation(BuiltInRegistries.BLOCK.getKey(block));
        buf.writeBoolean(consume);
    }

    public static StructureRequirement readFromBuf(FriendlyByteBuf buf) {
        return new StructureRequirement(
                buf.readBlockPos(),
                BuiltInRegistries.BLOCK.by(buf.readResourceLocation()),
                buf.readBoolean());
    }
}
