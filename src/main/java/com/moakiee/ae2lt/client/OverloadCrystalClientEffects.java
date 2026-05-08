package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.OverloadCrystalItem;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class OverloadCrystalClientEffects {
    private OverloadCrystalClientEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null) {
            return;
        }

        if (player.getMainHandItem().is(ModItems.OVERLOAD_CRYSTAL.get())) {
            OverloadCrystalItem.spawnHeldLightning(level, player, true);
        }
        if (player.getOffhandItem().is(ModItems.OVERLOAD_CRYSTAL.get())) {
            OverloadCrystalItem.spawnHeldLightning(level, player, false);
        }
    }
}
