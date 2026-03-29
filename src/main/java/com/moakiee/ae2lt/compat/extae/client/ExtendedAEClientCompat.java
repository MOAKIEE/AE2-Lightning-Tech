package com.moakiee.ae2lt.compat.extae.client;

import appeng.init.client.InitScreens;

import com.moakiee.ae2lt.compat.extae.OverloadedWirelessConnectorMenu;
import com.moakiee.ae2lt.compat.extae.OverloadedWirelessHubMenu;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ExtendedAEClientCompat {

    private ExtendedAEClientCompat() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, OverloadedWirelessConnectorMenu.TYPE, OverloadedWirelessConnectorScreen::new,
                "/screens/wireless_connector.json");
        InitScreens.register(event, OverloadedWirelessHubMenu.TYPE, OverloadedWirelessHubScreen::new,
                "/screens/wireless_hub.json");
    }
}
