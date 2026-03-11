package com.moakiee.ae2lt.integration.ponder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class PonderScenes {
    private static final ResourceLocation FLAWLESS_BUDDING_OVERLOAD_CRYSTAL =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "flawless_budding_overload_crystal");
    private static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.2F), 1.2F);
    private static final DustParticleOptions PURPLE_DUST =
            new DustParticleOptions(new Vector3f(0.78F, 0.34F, 1.0F), 1.0F);

    private PonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(
                FLAWLESS_BUDDING_OVERLOAD_CRYSTAL,
                "flawless_budding_overload_crystal",
                PonderScenes::flawlessBuddingOverloadCrystal);
    }

    private static void flawlessBuddingOverloadCrystal(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("flawless_budding_overload_crystal", "Forging Flawless Budding Overload Crystal");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.95F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos center = util.grid().at(2, 1, 2);
        BlockPos rod = center.above();

        Selection centerBlock = util.select().position(center);
        Selection rodBlock = util.select().position(rod);
        Selection fluixBlocks = util.select().position(2, 1, 1)
                .add(util.select().position(1, 1, 2))
                .add(util.select().position(3, 1, 2))
                .add(util.select().position(2, 1, 3));
        Selection overloadBlocks = util.select().position(1, 1, 1)
                .add(util.select().position(3, 1, 1))
                .add(util.select().position(1, 1, 3))
                .add(util.select().position(3, 1, 3));
        Selection ritualBlocks = centerBlock.copy().add(fluixBlocks).add(overloadBlocks);

        scene.world().showSection(centerBlock, Direction.DOWN);
        scene.overlay().showText(45)
                .pointAt(util.vector().topOf(center))
                .placeNearTarget()
                .text("Use a Flawless Budding Quartz block as the core.");
        scene.idle(50);

        scene.world().showSection(fluixBlocks, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "fluix_ring", fluixBlocks, 50);
        scene.overlay().showText(45)
                .pointAt(util.vector().centerOf(2, 1, 1))
                .placeNearTarget()
                .text("Place 4 Fluix Blocks on the four sides.");
        scene.idle(50);

        scene.world().showSection(overloadBlocks, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "overload_ring", overloadBlocks, 55);
        scene.overlay().showText(50)
                .pointAt(util.vector().centerOf(1, 1, 1))
                .placeNearTarget()
                .text("Place 4 Overload Crystal Blocks on the four corners.");
        scene.idle(60);

        scene.world().showSection(rodBlock, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.WHITE, "rod", rodBlock, 45);
        scene.overlay().showText(40)
                .pointAt(util.vector().topOf(rod))
                .placeNearTarget()
                .text("Mount a Lightning Rod directly above the core.");
        scene.idle(50);

        scene.addKeyframe();
        scene.overlay().showOutline(PonderPalette.GREEN, "ritual", ritualBlocks, 55);
        scene.overlay().showText(50)
                .pointAt(util.vector().topOf(rod))
                .placeNearTarget()
                .text("Only natural lightning can complete this multiblock.");
        scene.idle(60);

        scene.overlay().showText(55)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().centerOf(center))
                .placeNearTarget()
                .text("Artificial lightning from carried overload crystals is ignored.");
        scene.idle(65);

        scene.overlay().showBigLine(
                PonderPalette.WHITE,
                util.vector().of(2.5D, 4.8D, 2.5D),
                util.vector().topOf(rod),
                20);
        scene.idle(5);

        emitLightning(scene, util, center, rod);
        scene.idle(10);

        scene.world().setBlocks(fluixBlocks, Blocks.AIR.defaultBlockState(), true);
        scene.world().setBlocks(overloadBlocks, Blocks.AIR.defaultBlockState(), true);
        scene.world().setBlock(center, ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL.get().defaultBlockState(), true);
        scene.effects().indicateSuccess(center);
        scene.idle(10);

        scene.overlay().showText(70)
                .pointAt(util.vector().topOf(center))
                .placeNearTarget()
                .text("The eight surrounding blocks are consumed, leaving the flawless budding overload crystal.");
        scene.idle(80);

        scene.overlay().showText(50)
                .pointAt(util.vector().topOf(center))
                .placeNearTarget()
                .text("Use it to grow overload crystal buds and clusters.");
        scene.idle(70);
        scene.markAsFinished();
    }

    private static void emitLightning(SceneBuilder scene, SceneBuildingUtil util, BlockPos center, BlockPos rod) {
        ParticleEmitter sparkEmitter =
                scene.effects().simpleParticleEmitter(ParticleTypes.ELECTRIC_SPARK, new Vec3(0.0D, -0.12D, 0.0D));
        ParticleEmitter enchantEmitter =
                scene.effects().simpleParticleEmitter(ParticleTypes.ENCHANT, new Vec3(0.0D, 0.06D, 0.0D));
        ParticleEmitter endRodEmitter =
                scene.effects().simpleParticleEmitter(ParticleTypes.END_ROD, Vec3.ZERO);
        ParticleEmitter goldEmitter = scene.effects().simpleParticleEmitter(GOLD_DUST, Vec3.ZERO);
        ParticleEmitter purpleEmitter = scene.effects().simpleParticleEmitter(PURPLE_DUST, Vec3.ZERO);

        Vec3 rodTop = util.vector().topOf(rod);
        Vec3 centerTop = util.vector().topOf(center);

        scene.effects().emitParticles(util.vector().of(2.5D, 4.8D, 2.5D), sparkEmitter, 16, 6);
        scene.effects().emitParticles(rodTop, sparkEmitter, 14, 8);
        scene.effects().emitParticles(centerTop, enchantEmitter, 18, 10);
        scene.effects().emitParticles(centerTop, endRodEmitter, 6, 10);
        scene.effects().emitParticles(util.vector().centerOf(2, 1, 1), purpleEmitter, 16, 8);
        scene.effects().emitParticles(util.vector().centerOf(1, 1, 2), purpleEmitter, 16, 8);
        scene.effects().emitParticles(util.vector().centerOf(3, 1, 2), purpleEmitter, 16, 8);
        scene.effects().emitParticles(util.vector().centerOf(2, 1, 3), purpleEmitter, 16, 8);
        scene.effects().emitParticles(util.vector().centerOf(1, 1, 1), goldEmitter, 18, 8);
        scene.effects().emitParticles(util.vector().centerOf(3, 1, 1), goldEmitter, 18, 8);
        scene.effects().emitParticles(util.vector().centerOf(1, 1, 3), goldEmitter, 18, 8);
        scene.effects().emitParticles(util.vector().centerOf(3, 1, 3), goldEmitter, 18, 8);
    }
}
