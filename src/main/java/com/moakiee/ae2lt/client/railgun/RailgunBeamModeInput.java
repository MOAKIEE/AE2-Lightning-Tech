package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.network.railgun.RailgunBeamModePacket;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Client-side input: Shift + mouse-wheel while holding a railgun switches the
 * beam ammunition mode (HV / EHV). The event is consumed so the vanilla hotbar
 * doesn't scroll. The new mode is sent to the server via
 * {@link RailgunBeamModePacket}; the server is the authority that updates the
 * stack's {@link RailgunSettings}.
 *
 * <p>The client also predicts the new mode locally for an immediate HUD
 * response — if the server rejects the change (e.g. stack swapped mid-flight)
 * the next sync packet will revert the displayed value.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunBeamModeInput {

    private RailgunBeamModeInput() {}

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!mc.player.isShiftKeyDown()) return;

        // Pick the hand that actually holds the railgun (prefer main, fall back to off).
        InteractionHand hand;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.getItem() instanceof ElectromagneticRailgunItem) {
            hand = InteractionHand.MAIN_HAND;
        } else {
            stack = mc.player.getOffhandItem();
            if (!(stack.getItem() instanceof ElectromagneticRailgunItem)) return;
            hand = InteractionHand.OFF_HAND;
        }

        // Eat the scroll: don't let it move the hotbar.
        e.setCanceled(true);

        RailgunSettings settings = stack.getOrDefault(
                ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        RailgunSettings.BeamMode next = settings.beamMode().next();
        if (next == settings.beamMode()) return;

        // Predict locally so the HUD flips immediately.
        stack.set(ModDataComponents.RAILGUN_SETTINGS.get(), settings.withBeamMode(next));

        // Tell the player which mode they switched to.
        Component msg = Component.translatable(
                "ae2lt.railgun.beam_mode.switched",
                Component.translatable("ae2lt.railgun.beam_mode." + next.getSerializedName()));
        mc.player.displayClientMessage(msg, true);

        PacketDistributor.sendToServer(new RailgunBeamModePacket(next.ordinal(), hand));
    }
}
