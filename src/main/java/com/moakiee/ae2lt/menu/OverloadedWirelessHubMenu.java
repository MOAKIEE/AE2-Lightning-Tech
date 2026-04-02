package com.moakiee.ae2lt.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.Nullable;

import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.UpgradeableMenu;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedWirelessHubBlockEntity;
import com.moakiee.ae2lt.machine.wireless.WirelessStatus;

public class OverloadedWirelessHubMenu extends UpgradeableMenu<OverloadedWirelessHubBlockEntity> {

    public static final MenuType<OverloadedWirelessHubMenu> TYPE = MenuTypeBuilder
            .create(OverloadedWirelessHubMenu::new, OverloadedWirelessHubBlockEntity.class)
            .withMenuTitle(host -> host.getBlockState().getBlock().getName())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_wireless_hub"));

    private static final int MAX_PORT = OverloadedWirelessHubBlockEntity.MAX_PORT;

    @GuiSync(7) public double powerUse;
    @GuiSync(8) public int usedChannel;
    @GuiSync(9) public int maxChannel;

    // Port status (ordinal)
    @GuiSync(30) public int status0;
    @GuiSync(31) public int status1;
    @GuiSync(32) public int status2;
    @GuiSync(33) public int status3;
    @GuiSync(34) public int status4;
    @GuiSync(35) public int status5;
    @GuiSync(36) public int status6;
    @GuiSync(37) public int status7;

    // Port remote pos
    @GuiSync(40) public long remote0;
    @GuiSync(41) public long remote1;
    @GuiSync(42) public long remote2;
    @GuiSync(43) public long remote3;
    @GuiSync(44) public long remote4;
    @GuiSync(45) public long remote5;
    @GuiSync(46) public long remote6;
    @GuiSync(47) public long remote7;

    // Port remote channels
    @GuiSync(50) public int ch0;
    @GuiSync(51) public int ch1;
    @GuiSync(52) public int ch2;
    @GuiSync(53) public int ch3;
    @GuiSync(54) public int ch4;
    @GuiSync(55) public int ch5;
    @GuiSync(56) public int ch6;
    @GuiSync(57) public int ch7;

    public OverloadedWirelessHubMenu(int id, Inventory playerInventory,
            OverloadedWirelessHubBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        registerClientAction("disconnect", Integer.class, this::handleDisconnect);
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
            for (int i = 0; i < MAX_PORT; i++) {
                int st = getHost().getStatus(i).ordinal();
                var rp = getHost().getRemotePosition(i);
                long rl = rp != null ? rp.asLong() : 0;
                int rc = getHost().getRemoteChannels(i);
                setStatusOrd(i, st);
                setRemoteLong(i, rl);
                setChannelVal(i, rc);
            }
        }
        super.broadcastChanges();
    }

    public WirelessStatus getStatus(int port) {
        int ord = getStatusOrd(port);
        var values = WirelessStatus.values();
        return ord >= 0 && ord < values.length ? values[ord] : WirelessStatus.UNCONNECTED;
    }

    @Nullable
    public BlockPos getRemotePosition(int port) {
        long val = getRemoteLong(port);
        return val != 0 ? BlockPos.of(val) : null;
    }

    public int getRemoteChannel(int port) {
        return switch (port) {
            case 0 -> ch0; case 1 -> ch1; case 2 -> ch2; case 3 -> ch3;
            case 4 -> ch4; case 5 -> ch5; case 6 -> ch6; case 7 -> ch7;
            default -> 0;
        };
    }

    public void clientDisconnect(int port) {
        sendClientAction("disconnect", port);
    }

    private void handleDisconnect(int port) {
        if (isServerSide()) {
            getHost().killPort(port);
        }
    }

    private int getStatusOrd(int port) {
        return switch (port) {
            case 0 -> status0; case 1 -> status1; case 2 -> status2; case 3 -> status3;
            case 4 -> status4; case 5 -> status5; case 6 -> status6; case 7 -> status7;
            default -> 0;
        };
    }

    private void setStatusOrd(int port, int val) {
        switch (port) {
            case 0 -> status0 = val; case 1 -> status1 = val; case 2 -> status2 = val; case 3 -> status3 = val;
            case 4 -> status4 = val; case 5 -> status5 = val; case 6 -> status6 = val; case 7 -> status7 = val;
        }
    }

    private long getRemoteLong(int port) {
        return switch (port) {
            case 0 -> remote0; case 1 -> remote1; case 2 -> remote2; case 3 -> remote3;
            case 4 -> remote4; case 5 -> remote5; case 6 -> remote6; case 7 -> remote7;
            default -> 0;
        };
    }

    private void setRemoteLong(int port, long val) {
        switch (port) {
            case 0 -> remote0 = val; case 1 -> remote1 = val; case 2 -> remote2 = val; case 3 -> remote3 = val;
            case 4 -> remote4 = val; case 5 -> remote5 = val; case 6 -> remote6 = val; case 7 -> remote7 = val;
        }
    }

    private void setChannelVal(int port, int val) {
        switch (port) {
            case 0 -> ch0 = val; case 1 -> ch1 = val; case 2 -> ch2 = val; case 3 -> ch3 = val;
            case 4 -> ch4 = val; case 5 -> ch5 = val; case 6 -> ch6 = val; case 7 -> ch7 = val;
        }
    }
}
