package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class FrequencyCardScrollHandler {
    private FrequencyCardScrollHandler() {
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event.getScrollDeltaY() == 0.0D || !Minecraft.getInstance().hasShiftDown()) {
            return;
        }

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        InteractionHand hand = null;
        if (player.getMainHandItem().getItem() instanceof OverloadedFrequencyCardItem) {
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().getItem() instanceof OverloadedFrequencyCardItem) {
            hand = InteractionHand.OFF_HAND;
        }

        if (hand == null) {
            return;
        }

        ClientPacketDistributor.sendToServer(ToggleFrequencyCardAutoConnectPacket.forHand(hand));
        event.setCanceled(true);
    }
}
