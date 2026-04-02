package com.moakiee.ae2lt.compat.extae;

import com.glodblock.github.extendedae.container.ContainerWirelessConnector;
import com.moakiee.ae2lt.AE2LightningTech;

import appeng.menu.implementations.MenuTypeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public class OverloadedWirelessConnectorMenu extends ContainerWirelessConnector {

    public static final MenuType<OverloadedWirelessConnectorMenu> TYPE = MenuTypeBuilder
            .create(OverloadedWirelessConnectorMenu::new, OverloadedWirelessConnectorBlockEntity.class)
            .withMenuTitle(host -> host.getBlockState().getBlock().getName())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_wireless_connector"));

    public OverloadedWirelessConnectorMenu(int id, Inventory playerInventory,
            OverloadedWirelessConnectorBlockEntity host) {
        super(id, playerInventory, host);
    }
}
