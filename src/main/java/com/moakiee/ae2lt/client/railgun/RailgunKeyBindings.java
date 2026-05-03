package com.moakiee.ae2lt.client.railgun;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
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
import com.moakiee.ae2lt.network.railgun.RailgunOpenGuiPacket;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
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

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (e.getEntity() != mc.player) return;
        while (OPEN_RAILGUN_GUI.consumeClick()) {
            ItemStack main = mc.player.getMainHandItem();
            ItemStack off = mc.player.getOffhandItem();
            InteractionHand hand;
            if (main.getItem() instanceof ElectromagneticRailgunItem) hand = InteractionHand.MAIN_HAND;
            else if (off.getItem() instanceof ElectromagneticRailgunItem) hand = InteractionHand.OFF_HAND;
            else continue;
            PacketDistributor.sendToServer(new RailgunOpenGuiPacket(hand));
        }
    }
}
