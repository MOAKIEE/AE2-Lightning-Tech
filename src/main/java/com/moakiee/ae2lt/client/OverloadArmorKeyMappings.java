package com.moakiee.ae2lt.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.OpenOverloadArmorMenuPacket;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class OverloadArmorKeyMappings {
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.ae2lt.open_overload_armor_config",
            GLFW.GLFW_KEY_G,
            "key.categories.ae2lt.overload_armor");

    private OverloadArmorKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    @EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class RuntimeHandler {
        private RuntimeHandler() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                return;
            }

            while (OPEN_CONFIG.consumeClick()) {
                PacketDistributor.sendToServer(new OpenOverloadArmorMenuPacket());
            }
        }
    }
}
