package com.moakiee.ae2lt.compat.extae;

import com.glodblock.github.extendedae.container.ContainerWirelessHub;
import com.moakiee.ae2lt.AE2LightningTech;

import appeng.menu.implementations.MenuTypeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public class OverloadedWirelessHubMenu extends ContainerWirelessHub {

    public static final MenuType<OverloadedWirelessHubMenu> TYPE = MenuTypeBuilder
            .create(OverloadedWirelessHubMenu::new, OverloadedWirelessHubBlockEntity.class)
            .withMenuTitle(host -> host.getBlockState().getBlock().getName())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_wireless_hub"));

    public OverloadedWirelessHubMenu(int id, Inventory playerInventory, OverloadedWirelessHubBlockEntity host) {
        super(id, playerInventory, host);
    }
}
