package com.moakiee.ae2lt.integration.jei;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.client.MultiblockPreviewPictureInPictureRenderer;

import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3x2f;

/**
 * Renders a small isometric 3D multiblock preview inside a JEI recipe layout.
 *
 * <p>Each block is placed at its world-space {@link BlockPos} offset; the widget
 * auto-fits the structure to its rectangle, gently rotates around the Y axis, and
 * submits a 26.1 picture-in-picture render state so the whole structure rotates
 * as one 3D model.</p>
 */
public final class MultiblockPreviewWidget implements IRecipeWidget {
    private static final float X_ROTATION_DEG = 30.0F;
    private static final float SCALE_MARGIN = 0.92F;
    private static final float ROTATION_PER_FRAME = 0.4F;

    private final ScreenPosition position;
    private final int width;
    private final int height;
    private final List<MultiblockPreviewPictureInPictureRenderer.BlockEntry> blocks;

    private final float centerX;
    private final float centerY;
    private final float centerZ;
    private final float scale;

    private float rotation;

    private MultiblockPreviewWidget(
            int x,
            int y,
            int width,
            int height,
            List<MultiblockPreviewPictureInPictureRenderer.BlockEntry> blocks) {
        this.position = new ScreenPosition(x, y);
        this.width = width;
        this.height = height;
        this.blocks = blocks;

        if (blocks.isEmpty()) {
            this.centerX = 0;
            this.centerY = 0;
            this.centerZ = 0;
            this.scale = 0;
            return;
        }

        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (var entry : blocks) {
            minX = Math.min(minX, entry.offset().getX() - 0.5F);
            maxX = Math.max(maxX, entry.offset().getX() + 0.5F);
            minY = Math.min(minY, entry.offset().getY() - 0.5F);
            maxY = Math.max(maxY, entry.offset().getY() + 0.5F);
            minZ = Math.min(minZ, entry.offset().getZ() - 0.5F);
            maxZ = Math.max(maxZ, entry.offset().getZ() + 0.5F);
        }
        this.centerX = (minX + maxX) * 0.5F;
        this.centerY = (minY + maxY) * 0.5F;
        this.centerZ = (minZ + maxZ) * 0.5F;

        float xRotCos = (float) Math.cos(Math.toRadians(X_ROTATION_DEG));
        float xRotSin = (float) Math.sin(Math.toRadians(X_ROTATION_DEG));
        float horizontalRadius = 0F;
        float verticalRadius = 0F;
        float[] xs = {minX - centerX, maxX - centerX};
        float[] ys = {minY - centerY, maxY - centerY};
        float[] zs = {minZ - centerZ, maxZ - centerZ};
        for (float xv : xs) {
            for (float yv : ys) {
                for (float zv : zs) {
                    float horizontal = (float) Math.hypot(xv, zv);
                    horizontalRadius = Math.max(horizontalRadius, horizontal);
                    verticalRadius = Math.max(
                            verticalRadius,
                            Math.abs(yv) * xRotCos + horizontal * xRotSin);
                }
            }
        }
        if (horizontalRadius <= 0F || verticalRadius <= 0F) {
            this.scale = 0F;
            return;
        }
        float widthScale = width * 0.5F / horizontalRadius;
        float heightScale = height * 0.5F / verticalRadius;
        this.scale = Math.min(widthScale, heightScale) * SCALE_MARGIN;
    }

    @Override
    public ScreenPosition getPosition() {
        return position;
    }

    @Override
    public void drawWidget(GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        if (blocks.isEmpty() || scale <= 0F) {
            return;
        }

        var rect = new ScreenRectangle(0, 0, width, height);
        var screenBounds = rect.transformMaxBounds(guiGraphics.pose());
        var scissorArea = guiGraphics.peekScissorStack();
        screenBounds = scissorArea != null ? scissorArea.intersection(screenBounds) : screenBounds;

        guiGraphics.submitPictureInPictureRenderState(
                new MultiblockPreviewPictureInPictureRenderer.State(
                        new Matrix3x2f(guiGraphics.pose()),
                        rect.left(),
                        rect.top(),
                        rect.right(),
                        rect.bottom(),
                        screenBounds,
                        scissorArea,
                        blocks,
                        scale,
                        centerX,
                        centerY,
                        centerZ,
                        rotation));
    }

    @Override
    public void tick() {
        rotation += ROTATION_PER_FRAME;
        if (rotation >= 360F) {
            rotation -= 360F;
        }
    }

    public static Builder builder(int x, int y, int width, int height) {
        return new Builder(x, y, width, height);
    }

    public static final class Builder {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final List<MultiblockPreviewPictureInPictureRenderer.BlockEntry> blocks = new ArrayList<>();

        private Builder(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Builder addBlock(Block block, BlockPos offset) {
            if (block == null) {
                return this;
            }
            blocks.add(new MultiblockPreviewPictureInPictureRenderer.BlockEntry(block.defaultBlockState(), offset));
            return this;
        }

        public MultiblockPreviewWidget build() {
            return new MultiblockPreviewWidget(x, y, width, height, List.copyOf(blocks));
        }
    }
}
