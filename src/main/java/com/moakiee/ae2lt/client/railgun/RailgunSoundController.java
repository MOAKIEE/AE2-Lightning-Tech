package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;

/**
 * Client-side sound controller for sustained railgun audio.
 *
 * <p>Ticks every frame: starts/stops {@link RailgunBeamLoopSound} based on
 * {@link RailgunBeamRenderClient#isLocalFiring()}, and starts/stops
 * {@link RailgunChargeLoopSound} based on {@code isUsingItem()}.
 * Only one instance of each sound is held at a time. Forced cleanup on
 * disconnect/reconnect.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunSoundController {

    private static RailgunBeamLoopSound beamSound;
    private static RailgunChargeLoopSound chargeSound;

    private RailgunSoundController() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.getEntity() != mc.player) return;
        LocalPlayer player = mc.player;

        // -- Beam loop --
        boolean beamFiring = RailgunBeamRenderClient.isLocalFiring();
        if (beamFiring) {
            if (beamSound == null || beamSound.isStopped()) {
                beamSound = new RailgunBeamLoopSound(player);
                mc.getSoundManager().play(beamSound);
            }
        } else if (beamSound != null) {
            beamSound.requestStop();
            if (beamSound.isStopped()) {
                beamSound = null;
            }
        }

        // -- Charge loop --
        ItemStack using = player.getUseItem();
        boolean charging = player.isUsingItem()
                && using.getItem() instanceof ElectromagneticRailgunItem;
        if (charging) {
            if (chargeSound == null || chargeSound.isStopped()) {
                chargeSound = new RailgunChargeLoopSound(player);
                mc.getSoundManager().play(chargeSound);
            }
        } else if (chargeSound != null) {
            chargeSound.requestStop();
            if (chargeSound.isStopped()) {
                chargeSound = null;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        forceStopAll();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        forceStopAll();
    }

    private static void forceStopAll() {
        if (beamSound != null) {
            beamSound.requestStop();
            beamSound = null;
        }
        if (chargeSound != null) {
            chargeSound.requestStop();
            chargeSound = null;
        }
    }
}
