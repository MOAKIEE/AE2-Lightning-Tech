package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public final class MultiblockPreviewPictureInPictureRenderer
        extends PictureInPictureRenderer<MultiblockPreviewPictureInPictureRenderer.State> {
    private static final float X_ROTATION_DEG = 30.0F;
    private static final float Y_ROTATION_DEG = 225.0F;

    public MultiblockPreviewPictureInPictureRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    @Override
    protected void renderToTexture(State renderState, PoseStack poseStack) {
        var minecraft = Minecraft.getInstance();
        var modelSet = minecraft.getModelManager().getBlockStateModelSet();
        var blockColors = minecraft.getBlockColors();
        var level = new PreviewBlockAndTintGetter(renderState.blocks());
        var random = RandomSource.create(0L);
        var parts = new ArrayList<BlockStateModelPart>();

        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);

        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(X_ROTATION_DEG));
        poseStack.mulPose(Axis.YP.rotationDegrees(Y_ROTATION_DEG + renderState.rotation()));

        for (BlockEntry entry : renderState.blocks()) {
            BlockState blockState = entry.state();
            if (blockState.getRenderShape() != RenderShape.MODEL) {
                continue;
            }

            BlockPos pos = entry.offset();
            var model = modelSet.get(blockState);
            parts.clear();
            random.setSeed(pos.asLong());
            model.collectParts(level, pos, blockState, random, parts);
            if (parts.isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() - renderState.centerX() - 0.5F,
                    pos.getY() - renderState.centerY() - 0.5F,
                    pos.getZ() - renderState.centerZ() - 0.5F);

            for (BlockStateModelPart part : parts) {
                for (Direction direction : Direction.values()) {
                    renderQuads(part.getQuads(direction), blockState, level, pos, blockColors, poseStack);
                }
                renderQuads(part.getQuads(null), blockState, level, pos, blockColors, poseStack);
            }

            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private void renderQuads(
            List<net.minecraft.client.resources.model.geometry.BakedQuad> quads,
            BlockState blockState,
            BlockAndTintGetter level,
            BlockPos pos,
            net.minecraft.client.color.block.BlockColors blockColors,
            PoseStack poseStack) {
        for (var quad : quads) {
            var instance = new QuadInstance();
            instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

            int tintIndex = quad.materialInfo().tintIndex();
            if (tintIndex != -1) {
                var tintSource = blockColors.getTintSource(blockState, tintIndex);
                if (tintSource != null) {
                    instance.multiplyColor(tintSource.colorInWorld(blockState, level, pos));
                }
            }

            bufferSource.getBuffer(renderTypeFor(quad)).putBakedQuad(poseStack.last(), quad, instance);
        }
    }

    private static RenderType renderTypeFor(net.minecraft.client.resources.model.geometry.BakedQuad quad) {
        return quad.materialInfo().layer().translucent()
                ? Sheets.translucentBlockSheet()
                : Sheets.cutoutBlockSheet();
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "AE2LT JEI multiblock preview";
    }

    public record BlockEntry(BlockState state, BlockPos offset) {
    }

    public record State(
            Matrix3x2f pose,
            int x0,
            int y0,
            int x1,
            int y1,
            @Nullable ScreenRectangle bounds,
            @Nullable ScreenRectangle scissorArea,
            List<BlockEntry> blocks,
            float blockScale,
            float centerX,
            float centerY,
            float centerZ,
            float rotation) implements PictureInPictureRenderState {
        @Override
        public float scale() {
            return blockScale;
        }
    }

    private static final class PreviewBlockAndTintGetter implements BlockAndTintGetter {
        private final Map<BlockPos, BlockState> blocks;

        private PreviewBlockAndTintGetter(List<BlockEntry> entries) {
            var map = new HashMap<BlockPos, BlockState>();
            for (BlockEntry entry : entries) {
                map.put(entry.offset(), entry.state());
            }
            this.blocks = Map.copyOf(map);
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return -1;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            BlockState state = blocks.get(pos);
            return state != null ? state.getFluidState() : Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public int getMinY() {
            return 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
        }
    }
}
