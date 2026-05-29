package com.moakiee.ae2lt.lightning.strike;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;

public record StructureRequirement(BlockPos offset, Block block, boolean consume) {
    public static final Codec<StructureRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("offset").forGetter(StructureRequirement::offset),
                    ForgeRegistries.BLOCKS.getCodec().fieldOf("block").forGetter(StructureRequirement::block),
                    Codec.BOOL.optionalFieldOf("consume", false).forGetter(StructureRequirement::consume))
            .apply(instance, StructureRequirement::new));

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeBlockPos(offset);
        buf.writeVarInt(Block.getId(block.defaultBlockState()));
        buf.writeBoolean(consume);
    }

    public static StructureRequirement readFromBuf(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Block block = Block.stateById(buf.readVarInt()).getBlock();
        boolean consume = buf.readBoolean();
        return new StructureRequirement(pos, block, consume);
    }
}
