package com.moakiee.ae2lt.client;

import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import appeng.api.client.AEKeyRendering;
import appeng.items.storage.BasicStorageCell;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.moakiee.ae2lt.registry.ModItems;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LightningKeyClientInit {
    private LightningKeyClientInit() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                AEKeyRendering.register(LightningKeyType.INSTANCE, LightningKey.class, LightningKeyRenderHandler.INSTANCE));
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> FastColor.ARGB32.opaque(BasicStorageCell.getColor(stack, tintIndex)),
                ModItems.LIGHTNING_STORAGE_COMPONENT_I.get(),
                ModItems.LIGHTNING_STORAGE_COMPONENT_II.get(),
                ModItems.LIGHTNING_STORAGE_COMPONENT_III.get(),
                ModItems.LIGHTNING_STORAGE_COMPONENT_IV.get(),
                ModItems.LIGHTNING_STORAGE_COMPONENT_V.get());
    }
}
