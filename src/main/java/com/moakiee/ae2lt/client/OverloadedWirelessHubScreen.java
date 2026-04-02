package com.moakiee.ae2lt.client;

import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.blockentity.OverloadedWirelessHubBlockEntity;
import com.moakiee.ae2lt.client.widget.HighlightButton;
import com.moakiee.ae2lt.machine.wireless.WirelessStatus;
import com.moakiee.ae2lt.menu.OverloadedWirelessHubMenu;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.AppEng;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class OverloadedWirelessHubScreen extends UpgradeableScreen<OverloadedWirelessHubMenu> {

    private static final int MAX_PORT = OverloadedWirelessHubBlockEntity.MAX_PORT;
    private static final Blitter PORT =
            Blitter.texture(AppEng.makeId("textures/guis/wireless_hub.png")).src(176, 0, 16, 16);
    private static final int COL1_X = 37;
    private static final int COL2_X = 120;
    private static final int COL_Y = 44;
    private static final int Y_OFFSET = 28;
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 6;

    private final RemoteBlock[] remotes = new RemoteBlock[MAX_PORT];
    private final HighlightButton[] highlightBtn = new HighlightButton[MAX_PORT];
    private final Button[] disconnectBtn = new Button[MAX_PORT];

    public OverloadedWirelessHubScreen(
            OverloadedWirelessHubMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        for (int i = 0; i < MAX_PORT; i++) {
            final int port = i;
            this.remotes[i] = new RemoteBlock(() -> menu.getRemotePosition(port), 0, 0, 16, 16);
            this.highlightBtn[i] = new HighlightButton(0, 0);
            this.highlightBtn[i].setTooltip(Tooltip.create(
                    Component.translatable("gui.ae2lt.wireless.highlight.tooltip")));
            this.disconnectBtn[i] = Button.builder(
                    Component.literal("X"),
                    b -> menu.clientDisconnect(port))
                    .bounds(0, 0, 16, 16)
                    .build();
            this.disconnectBtn[i].setTooltip(Tooltip.create(
                    Component.translatable("gui.ae2lt.wireless.hub.disconnect.tooltip")));
        }
    }

    @Override
    public void init() {
        super.init();
        for (int i = 0; i < MAX_PORT; i++) {
            int x = i < 4 ? COL1_X : COL2_X;
            int y = i % 4;
            this.remotes[i].setPosition(this.leftPos + x, this.topPos + COL_Y + y * Y_OFFSET);
            this.highlightBtn[i].setPosition(this.leftPos + x - 18, this.topPos + COL_Y + y * Y_OFFSET - 2);
            this.disconnectBtn[i].setPosition(this.leftPos + x + 18, this.topPos + COL_Y + y * Y_OFFSET - 2);
            this.addRenderableOnly(this.remotes[i]);
            this.addRenderableWidget(this.highlightBtn[i]);
            this.addRenderableWidget(this.disconnectBtn[i]);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        var dim = this.getPlayer().clientLevel.dimension();
        for (int i = 0; i < MAX_PORT; i++) {
            var status = this.menu.getStatus(i);
            if (status == WirelessStatus.WORKING || status == WirelessStatus.NO_POWER) {
                var remotePos = this.menu.getRemotePosition(i);
                this.highlightBtn[i].active = true;
                this.highlightBtn[i].visible = true;
                this.highlightBtn[i].setTarget(remotePos, dim);
                this.highlightBtn[i].setMultiplier(this.playerToBlockDis(remotePos));
                this.remotes[i].setTooltip(Tooltip.create(
                        Component.translatable("gui.ae2lt.wireless.remote_channel",
                                remotePos.getX(), remotePos.getY(), remotePos.getZ(),
                                this.menu.getRemoteChannel(i))));
                this.remotes[i].setConnected(true);
                this.disconnectBtn[i].active = true;
                this.disconnectBtn[i].visible = true;
            } else {
                this.highlightBtn[i].active = false;
                this.highlightBtn[i].visible = false;
                this.remotes[i].setTooltip(Tooltip.create(
                        Component.translatable("gui.ae2lt.wireless.hub.empty_port.tooltip")));
                this.remotes[i].setConnected(false);
                // Show disconnect only if frequency set but not connected
                boolean hasFreq = status != WirelessStatus.UNCONNECTED;
                this.disconnectBtn[i].active = hasFreq;
                this.disconnectBtn[i].visible = hasFreq;
            }
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        int len = 12;
        guiGraphics.drawString(this.font,
                Component.translatable("gui.ae2lt.wireless.power",
                        String.format("%.2f", this.menu.powerUse)),
                PADDING_X, PADDING_Y + len, textColor, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.ae2lt.wireless.channel",
                        this.menu.usedChannel, this.menu.maxChannel),
                PADDING_X, PADDING_Y + len * 2, textColor, false);
    }

    private double playerToBlockDis(BlockPos pos) {
        if (pos == null) {
            return 0;
        }
        var ps = this.getPlayer().getOnPos();
        return pos.distSqr(ps);
    }

    // ── Inner RemoteBlock widget (matches EAE's) ───────────────────────

    private static class RemoteBlock extends AbstractWidget {

        private final Supplier<BlockPos> locator;
        @Nullable
        private BlockPos localPos;
        private ItemStack localBlock = ItemStack.EMPTY;
        private boolean isConnected = false;

        public RemoteBlock(Supplier<BlockPos> locator, int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
            this.locator = locator;
        }

        private void setConnected(boolean connected) {
            this.isConnected = connected;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (this.localPos != this.locator.get() && Minecraft.getInstance().level != null) {
                this.localPos = this.locator.get();
                if (this.localPos != null) {
                    this.localBlock = new ItemStack(
                            Minecraft.getInstance().level.getBlockState(this.localPos).getBlock());
                }
            }
            if (this.isConnected && !this.localBlock.isEmpty()) {
                graphics.renderItem(this.localBlock, this.getX(), this.getY(), 0, 3);
            } else {
                PORT.dest(this.getX(), this.getY()).blit(graphics);
            }
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
        }
    }
}
