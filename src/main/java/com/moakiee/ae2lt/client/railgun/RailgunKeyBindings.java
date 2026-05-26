package com.moakiee.ae2lt.client.railgun;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.hub.OpenDeviceHubPacket;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RailgunKeyBindings {

    public static final String CATEGORY = "key.categories.ae2lt";
    public static final KeyMapping OPEN_RAILGUN_GUI = new KeyMapping(
            "key.ae2lt.railgun_gui",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_G),
            CATEGORY);

    private RailgunKeyBindings() {}

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent e) {
        e.register(OPEN_RAILGUN_GUI);
    }

    @EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class RuntimeHandler {
        private RuntimeHandler() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            if (e.getEntity() != mc.player) return;
            while (OPEN_RAILGUN_GUI.consumeClick()) {
                ItemStack main = mc.player.getMainHandItem();
                ItemStack off = mc.player.getOffhandItem();
                if (main.getItem() instanceof ElectromagneticRailgunItem
                        || off.getItem() instanceof ElectromagneticRailgunItem) {
                    PacketDistributor.sendToServer(new OpenDeviceHubPacket(DeviceHubMenu.TAB_RAILGUN));
                }
            }
        }
    }
}
