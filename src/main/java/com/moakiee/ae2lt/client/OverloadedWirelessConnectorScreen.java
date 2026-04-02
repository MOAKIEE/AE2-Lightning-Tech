package com.moakiee.ae2lt.client;

import java.util.Objects;

import com.moakiee.ae2lt.client.widget.HighlightButton;
import com.moakiee.ae2lt.client.widget.RemoteBlockPreview;
import com.moakiee.ae2lt.client.widget.WirelessHighlightRenderer;
import com.moakiee.ae2lt.machine.wireless.WirelessStatus;
import com.moakiee.ae2lt.menu.OverloadedWirelessConnectorMenu;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class OverloadedWirelessConnectorScreen extends UpgradeableScreen<OverloadedWirelessConnectorMenu> {

    public static final int PADDING_X = 8;
    public static final int PADDING_Y = 6;
    private final HighlightButton highlight;
    private final RemoteBlockPreview remote;
    private BlockPos lastPos = null;

    public OverloadedWirelessConnectorScreen(
            OverloadedWirelessConnectorMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.remote = new RemoteBlockPreview(0, 0, 129, 63);
        this.highlight = new HighlightButton(0, 0);
        this.highlight.setTooltip(Tooltip.create(Component.translatable("gui.ae2lt.wireless.highlight.tooltip")));
    }

    @Override
    public void init() {
        super.init();
        this.remote.setPosition(this.leftPos + 24, this.topPos + 76);
        this.highlight.setPosition(this.leftPos + 152, this.topPos + PADDING_Y + 21);
        this.addRenderableOnly(this.remote);
        this.addRenderableWidget(this.highlight);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (this.menu.getWirelessStatus() == WirelessStatus.WORKING && this.menu.hasRemote) {
            var remotePos = BlockPos.of(this.menu.otherSide);
            if (!Objects.equals(remotePos, this.lastPos)) {
                this.remote.locate(remotePos);
                this.lastPos = remotePos;
            }
            this.highlight.setTarget(remotePos, this.getPlayer().clientLevel.dimension());
            this.highlight.setMultiplier(this.playerToBlockDis(remotePos));
            this.highlight.visible = true;
            this.highlight.active = true;
        } else {
            this.remote.unload();
            this.highlight.visible = false;
            this.highlight.active = false;
            this.lastPos = null;
        }
    }

    private double playerToBlockDis(BlockPos pos) {
        if (pos == null) {
            return 0;
        }
        var ps = this.getPlayer().getOnPos();
        return pos.distSqr(ps);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        int len = 12;

        var status = this.menu.getWirelessStatus();

        guiGraphics.drawString(this.font,
                Component.translatable("gui.ae2lt.wireless.status", status.getTranslation()),
                PADDING_X, PADDING_Y + len, textColor, false);

        guiGraphics.drawString(this.font,
                Component.translatable("gui.ae2lt.wireless.power", String.format("%.2f", this.menu.powerUse)),
                PADDING_X, PADDING_Y + len * 2, textColor, false);

        guiGraphics.drawString(this.font,
                Component.translatable("gui.ae2lt.wireless.channel", this.menu.usedChannel, this.menu.maxChannel),
                PADDING_X, PADDING_Y + len * 3, textColor, false);

        if (status == WirelessStatus.WORKING && this.menu.hasRemote) {
            var pos = BlockPos.of(this.menu.otherSide);
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.ae2lt.wireless.remote", pos.getX(), pos.getY(), pos.getZ()),
                    22, 62, textColor, false);
        }
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (this.remote.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY)) {
            return true;
        }
        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        if (this.remote.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY)) {
            return true;
        }
        return super.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY);
    }
}
