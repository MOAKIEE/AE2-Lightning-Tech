package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class EasterEggClientTick {
    private EasterEggClientTick() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EasterEggOverlay.tick();
    }
}
