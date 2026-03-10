package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class NaturalLightningTransformationHandler {
    private static final ResourceLocation AE2_FLUIX_BLOCK_ID = ResourceLocation.parse("ae2:fluix_block");
    private static final ResourceLocation AE2_FLAWLESS_BUDDING_QUARTZ_ID =
            ResourceLocation.parse("ae2:flawless_budding_quartz");
    private static final String TRANSFORMATION_CHECKED_TAG = "ae2lt.natural_transform_checked";

    private static final List<BlockPos> OVERLOAD_BLOCK_OFFSETS = List.of(
            new BlockPos(-1, 0, -1),
            new BlockPos(1, 0, -1),
            new BlockPos(-1, 0, 1),
            new BlockPos(1, 0, 1));

    private static final List<BlockPos> FLUIX_BLOCK_OFFSETS = List.of(
            new BlockPos(0, 0, -1),
            new BlockPos(-1, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(0, 0, 1));

    private NaturalLightningTransformationHandler() {
    }

    @SubscribeEvent
    public static void onLightningTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LightningBolt lightningBolt)
                || !(lightningBolt.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var data = lightningBolt.getPersistentData();
        if (data.getBoolean(TRANSFORMATION_CHECKED_TAG)
                || data.getBoolean(ArtificialLightningHandler.ARTIFICIAL_LIGHTNING_TAG)
                || lightningBolt.getCause() != null) {
            return;
        }

        data.putBoolean(TRANSFORMATION_CHECKED_TAG, true);
        tryTransformFromNearbyLightningRod(serverLevel, lightningBolt.blockPosition());
    }

    private static void tryTransformFromNearbyLightningRod(ServerLevel level, BlockPos lightningPos) {
        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            BlockPos rodPos = lightningPos.below(yOffset);
            BlockState rodState = level.getBlockState(rodPos);
            if (!rodState.is(Blocks.LIGHTNING_ROD)) {
                continue;
            }

            BlockPos centerPos = rodPos.below();
            if (!matchesStructure(level, centerPos)) {
                continue;
            }

            consumeOuterStructure(level, centerPos);
            level.setBlockAndUpdate(centerPos, ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL.get().defaultBlockState());
            return;
        }
    }

    private static boolean matchesStructure(ServerLevel level, BlockPos centerPos) {
        BlockState centerState = level.getBlockState(centerPos);
        BlockState rodState = level.getBlockState(centerPos.above());

        if (!isBlock(centerState, AE2_FLAWLESS_BUDDING_QUARTZ_ID)) {
            return false;
        }

        if (!rodState.is(Blocks.LIGHTNING_ROD)) {
            return false;
        }

        for (BlockPos offset : OVERLOAD_BLOCK_OFFSETS) {
            BlockPos checkPos = centerPos.offset(offset);
            BlockState state = level.getBlockState(checkPos);
            if (!state.is(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get())) {
                return false;
            }
        }

        for (BlockPos offset : FLUIX_BLOCK_OFFSETS) {
            BlockPos checkPos = centerPos.offset(offset);
            BlockState state = level.getBlockState(checkPos);
            if (!isBlock(state, AE2_FLUIX_BLOCK_ID)) {
                return false;
            }
        }

        return true;
    }

    private static void consumeOuterStructure(ServerLevel level, BlockPos centerPos) {
        for (BlockPos offset : OVERLOAD_BLOCK_OFFSETS) {
            level.setBlockAndUpdate(centerPos.offset(offset), Blocks.AIR.defaultBlockState());
        }

        for (BlockPos offset : FLUIX_BLOCK_OFFSETS) {
            level.setBlockAndUpdate(centerPos.offset(offset), Blocks.AIR.defaultBlockState());
        }
    }

    private static boolean isBlock(BlockState state, ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id).map(state::is).orElse(false);
    }
}
