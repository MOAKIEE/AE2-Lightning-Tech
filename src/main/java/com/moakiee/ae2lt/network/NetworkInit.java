package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkInit {
    private NetworkInit() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(AE2LightningTech.MODID);
        registrar.playToServer(
                WirelessConnectorUsePacket.TYPE,
                WirelessConnectorUsePacket.STREAM_CODEC,
                WirelessConnectorUsePacket::handle);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }
}
