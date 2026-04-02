package com.moakiee.ae2lt.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.UpgradeableMenu;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedWirelessConnectorBlockEntity;
import com.moakiee.ae2lt.machine.wireless.WirelessStatus;

public class OverloadedWirelessConnectorMenu extends UpgradeableMenu<OverloadedWirelessConnectorBlockEntity> {

    public static final MenuType<OverloadedWirelessConnectorMenu> TYPE = MenuTypeBuilder
            .create(OverloadedWirelessConnectorMenu::new, OverloadedWirelessConnectorBlockEntity.class)
            .withMenuTitle(host -> host.getBlockState().getBlock().getName())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_wireless_connector"));

    @GuiSync(7)
    public double powerUse;
    @GuiSync(8)
    public int usedChannel;
    @GuiSync(9)
    public int maxChannel;
    @GuiSync(10)
    public long otherSide;
    @GuiSync(11)
    public int status;
    @GuiSync(12)
    public boolean hasRemote;

    public OverloadedWirelessConnectorMenu(int id, Inventory playerInventory,
            OverloadedWirelessConnectorBlockEntity host) {
        super(TYPE, id, playerInventory, host);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.powerUse = getHost().getPowerUse();
            var node = getHost().getMainNode().getNode();
            if (node != null) {
                this.usedChannel = node.getUsedChannels();
                this.maxChannel = node.getMaxChannels();
            } else {
                this.usedChannel = 0;
                this.maxChannel = 0;
            }
            var remotePos = getHost().getRemotePosition();
            this.hasRemote = remotePos != null;
            this.otherSide = remotePos != null ? remotePos.asLong() : 0;
            this.status = getHost().getStatus().ordinal();
        }
        super.broadcastChanges();
    }

    public WirelessStatus getWirelessStatus() {
        var values = WirelessStatus.values();
        return status >= 0 && status < values.length ? values[status] : WirelessStatus.UNCONNECTED;
    }
}
