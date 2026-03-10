package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class NaturalLightningTransformationHandler {
    private static final ResourceLocation AE2_FLUIX_BLOCK_ID = ResourceLocation.parse("ae2:fluix_block");
    private static final ResourceLocation AE2_FLAWLESS_BUDDING_QUARTZ_ID =
            ResourceLocation.parse("ae2:flawless_budding_quartz");
    private static final String TRANSFORMATION_CHECKED_TAG = "ae2lt.natural_transform_checked";
    private static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.2F), 1.6F);
    private static final DustParticleOptions PURPLE_DUST =
            new DustParticleOptions(new Vector3f(0.78F, 0.34F, 1.0F), 1.4F);

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
    private static final List<BlockPos> OUTER_RING_OFFSETS = List.of(
            new BlockPos(-2, 0, -2),
            new BlockPos(-1, 0, -2),
            new BlockPos(0, 0, -2),
            new BlockPos(1, 0, -2),
            new BlockPos(2, 0, -2),
            new BlockPos(-2, 0, -1),
            new BlockPos(2, 0, -1),
            new BlockPos(-2, 0, 0),
            new BlockPos(2, 0, 0),
            new BlockPos(-2, 0, 1),
            new BlockPos(2, 0, 1),
            new BlockPos(-2, 0, 2),
            new BlockPos(-1, 0, 2),
            new BlockPos(0, 0, 2),
            new BlockPos(1, 0, 2),
            new BlockPos(2, 0, 2));

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

            spawnTransformationParticles(level, rodPos, centerPos);
            consumeOuterStructure(level, centerPos);
            level.setBlockAndUpdate(centerPos, ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL.get().defaultBlockState());
            spawnCompletionParticles(level, centerPos);
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

    private static void spawnTransformationParticles(ServerLevel level, BlockPos rodPos, BlockPos centerPos) {
        Vec3 rodVec = Vec3.atCenterOf(rodPos).add(0.0D, -0.2D, 0.0D);
        Vec3 centerVec = Vec3.atCenterOf(centerPos).add(0.0D, 0.55D, 0.0D);

        // A much denser vertical current under the lightning rod.
        for (int i = 0; i < 7; i++) {
            double progress = i / 6.0D;
            Vec3 point = rodVec.lerp(centerVec, progress);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 10, 0.12D, 0.1D, 0.12D, 0.03D);
            level.sendParticles(GOLD_DUST, point.x, point.y, point.z, 8, 0.08D, 0.08D, 0.08D, 0.01D);
        }

        // Purple fluix energy pulls inward from the four sides in thick trails.
        for (BlockPos offset : FLUIX_BLOCK_OFFSETS) {
            Vec3 from = Vec3.atCenterOf(centerPos.offset(offset)).add(0.0D, 0.55D, 0.0D);
            Vec3 toward = from.vectorTo(centerVec).scale(0.18D);
            level.sendParticles(PURPLE_DUST, from.x, from.y, from.z, 24, 0.18D, 0.14D, 0.18D, 0.01D);
            level.sendParticles(ParticleTypes.WITCH, from.x, from.y, from.z, 20, toward.x, 0.06D, toward.z, 0.18D);
        }

        // Overload crystal corners throw larger golden trails toward the center.
        for (BlockPos offset : OVERLOAD_BLOCK_OFFSETS) {
            Vec3 from = Vec3.atCenterOf(centerPos.offset(offset)).add(0.0D, 0.55D, 0.0D);
            Vec3 toward = from.vectorTo(centerVec).scale(0.16D);
            level.sendParticles(GOLD_DUST, from.x, from.y, from.z, 26, 0.2D, 0.16D, 0.2D, 0.01D);
            level.sendParticles(ParticleTypes.ENCHANT, from.x, from.y, from.z, 20, toward.x, 0.06D, toward.z, 0.22D);
        }

        // A larger 5x5 outer ring slowly collapses inward so the effect reads from farther away.
        for (int i = 0; i < OUTER_RING_OFFSETS.size(); i++) {
            BlockPos offset = OUTER_RING_OFFSETS.get(i);
            Vec3 from = Vec3.atCenterOf(centerPos.offset(offset)).add(0.0D, 0.2D + (i % 3) * 0.12D, 0.0D);
            Vec3 toward = from.vectorTo(centerVec).scale(0.08D);
            DustParticleOptions ringDust = (i & 1) == 0 ? PURPLE_DUST : GOLD_DUST;
            level.sendParticles(ringDust, from.x, from.y, from.z, 12, 0.14D, 0.06D, 0.14D, 0.01D);
            level.sendParticles(ParticleTypes.ENCHANT, from.x, from.y, from.z, 8, toward.x, 0.03D, toward.z, 0.1D);
        }
    }

    private static void spawnCompletionParticles(ServerLevel level, BlockPos centerPos) {
        Vec3 centerVec = Vec3.atCenterOf(centerPos).add(0.0D, 0.7D, 0.0D);
        for (int i = 0; i < 4; i++) {
            double y = centerVec.y + i * 0.18D;
            level.sendParticles(GOLD_DUST, centerVec.x, y, centerVec.z, 18, 0.24D, 0.04D, 0.24D, 0.01D);
            level.sendParticles(ParticleTypes.END_ROD, centerVec.x, y, centerVec.z, 10, 0.18D, 0.1D, 0.18D, 0.03D);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centerVec.x, centerVec.y + 0.2D, centerVec.z, 24, 0.28D, 0.28D, 0.28D, 0.02D);
        level.sendParticles(ParticleTypes.ENCHANT, centerVec.x, centerVec.y + 0.25D, centerVec.z, 32, 0.35D, 0.25D, 0.35D, 0.12D);
    }
}
