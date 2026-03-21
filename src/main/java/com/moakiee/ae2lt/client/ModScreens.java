package com.moakiee.ae2lt.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.client.gui.style.StyleManager;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;

/**
 * Client event: binds MenuType to Screen.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModScreens {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(OverloadedPatternProviderMenu.TYPE, ModScreens::createOverloadedPatternProviderScreen);
        event.register(OverloadPatternEncoderMenu.TYPE, OverloadPatternEncoderScreen::new);
        event.register(LightningSimulationChamberMenu.TYPE, ModScreens::createLightningSimulationChamberScreen);
    }

    private static OverloadedPatternProviderScreen createOverloadedPatternProviderScreen(
            OverloadedPatternProviderMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overloaded_pattern_provider.json");
        return new OverloadedPatternProviderScreen(menu, inv, title, style);
    }

    private static LightningSimulationChamberScreen createLightningSimulationChamberScreen(
            LightningSimulationChamberMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/lightning_simulation_chamber.json");
        return new LightningSimulationChamberScreen(menu, inv, title, style);
    }
}
