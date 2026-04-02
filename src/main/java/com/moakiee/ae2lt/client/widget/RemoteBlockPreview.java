package com.moakiee.ae2lt.client.widget;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a preview of the block at a remote position.
 * Supports both small (16x16 item icon) and large (world preview area) sizes.
 */
public class RemoteBlockPreview extends AbstractWidget {

    @Nullable
    private BlockPos currentPos;
    private ItemStack displayItem = ItemStack.EMPTY;
    private boolean ready;

    public RemoteBlockPreview(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public void locate(BlockPos pos) {
        this.ready = false;
        if (pos == null) {
            return;
        }
        var clientWorld = Minecraft.getInstance().level;
        if (clientWorld == null) {
            return;
        }
        var block = clientWorld.getBlockState(pos);
        if (block.isAir()) {
            return;
        }
        this.currentPos = pos;
        this.displayItem = new ItemStack(block.getBlock());
        this.ready = true;
    }

    public void unload() {
        this.ready = false;
        this.currentPos = null;
        this.displayItem = ItemStack.EMPTY;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw background area
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x40000000);
        graphics.renderOutline(getX(), getY(), width, height, 0x80808080);

        if (ready && !displayItem.isEmpty()) {
            // Center the item in the preview area
            int itemX = getX() + (width - 16) / 2;
            int itemY = getY() + (height - 16) / 2;

            // Render large centered item
            var pose = graphics.pose();
            pose.pushPose();
            float scale = Math.min(width, height) / 24.0F;
            pose.translate(itemX + 8, itemY + 8, 0);
            pose.scale(scale, scale, 1);
            pose.translate(-8, -8, 0);
            graphics.renderItem(displayItem, 0, 0);
            pose.popPose();
        }
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        // Placeholder for future world display rotation
        return false;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        // Placeholder for future world display zoom
        return false;
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }
}
