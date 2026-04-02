package com.moakiee.ae2lt.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.client.gui.style.StyleManager;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.menu.OverloadedWirelessConnectorMenu;
import com.moakiee.ae2lt.menu.OverloadedWirelessHubMenu;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;

/**
 * Client event: binds MenuType to Screen.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModScreens {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(OverloadedPatternProviderMenu.TYPE, ModScreens::createOverloadedPatternProviderScreen);
        event.register(OverloadPatternEncoderMenu.TYPE, OverloadPatternEncoderScreen::new);
        event.register(OverloadedInterfaceMenu.TYPE, ModScreens::createOverloadedInterfaceScreen);
        event.register(LightningSimulationChamberMenu.TYPE, ModScreens::createLightningSimulationChamberScreen);
        event.register(LightningCollectorMenu.TYPE, ModScreens::createLightningCollectorScreen);
        event.register(OverloadProcessingFactoryMenu.TYPE, ModScreens::createOverloadProcessingFactoryScreen);
        event.register(TeslaCoilMenu.TYPE, ModScreens::createTeslaCoilScreen);
        event.register(AtmosphericIonizerMenu.TYPE, ModScreens::createAtmosphericIonizerScreen);
        event.register(OverloadedWirelessConnectorMenu.TYPE,
                ModScreens::createOverloadedWirelessConnectorScreen);
        event.register(OverloadedWirelessHubMenu.TYPE,
                ModScreens::createOverloadedWirelessHubScreen);
    }

    private static OverloadedPatternProviderScreen createOverloadedPatternProviderScreen(
            OverloadedPatternProviderMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overloaded_pattern_provider.json");
        return new OverloadedPatternProviderScreen(menu, inv, title, style);
    }

    private static OverloadedInterfaceScreen createOverloadedInterfaceScreen(
            OverloadedInterfaceMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overloaded_interface.json");
        return new OverloadedInterfaceScreen(menu, inv, title, style);
    }

    private static LightningSimulationChamberScreen createLightningSimulationChamberScreen(
            LightningSimulationChamberMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/lightning_simulation_room.json");
        return new LightningSimulationChamberScreen(menu, inv, title, style);
    }

    private static LightningCollectorScreen createLightningCollectorScreen(
            LightningCollectorMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/lightning_collector.json");
        return new LightningCollectorScreen(menu, inv, title, style);
    }

    private static OverloadProcessingFactoryScreen createOverloadProcessingFactoryScreen(
            OverloadProcessingFactoryMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overload_processing_factory.json");
        return new OverloadProcessingFactoryScreen(menu, inv, title, style);
    }

    private static TeslaCoilScreen createTeslaCoilScreen(
            TeslaCoilMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/tesla_coil.json");
        return new TeslaCoilScreen(menu, inv, title, style);
    }

    private static AtmosphericIonizerScreen createAtmosphericIonizerScreen(
            AtmosphericIonizerMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/atmospheric_ionizer.json");
        return new AtmosphericIonizerScreen(menu, inv, title, style);
    }

    private static OverloadedWirelessConnectorScreen createOverloadedWirelessConnectorScreen(
            OverloadedWirelessConnectorMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overloaded_wireless_connector.json");
        return new OverloadedWirelessConnectorScreen(menu, inv, title, style);
    }

    private static OverloadedWirelessHubScreen createOverloadedWirelessHubScreen(
            OverloadedWirelessHubMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overloaded_wireless_hub.json");
        return new OverloadedWirelessHubScreen(menu, inv, title, style);
    }
}
