package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.railgun.RailgunBeamChainFxPacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamTogglePacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamUpdatePacket;
import com.moakiee.ae2lt.network.railgun.RailgunFirePacket;
import com.moakiee.ae2lt.network.railgun.RailgunOpenGuiPacket;
import com.moakiee.ae2lt.network.railgun.RailgunRecoilFxPacket;
import com.moakiee.ae2lt.network.railgun.RailgunSettingsTogglePacket;
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

        // frequency system: C→S
        registrar.playToServer(
                CreateFrequencyPacket.TYPE,
                CreateFrequencyPacket.STREAM_CODEC,
                CreateFrequencyPacket::handle);
        registrar.playToServer(
                DeleteFrequencyPacket.TYPE,
                DeleteFrequencyPacket.STREAM_CODEC,
                DeleteFrequencyPacket::handle);
        registrar.playToServer(
                EditFrequencyPacket.TYPE,
                EditFrequencyPacket.STREAM_CODEC,
                EditFrequencyPacket::handle);
        registrar.playToServer(
                SelectFrequencyPacket.TYPE,
                SelectFrequencyPacket.STREAM_CODEC,
                SelectFrequencyPacket::handle);
        registrar.playToServer(
                ChangeMemberPacket.TYPE,
                ChangeMemberPacket.STREAM_CODEC,
                ChangeMemberPacket::handle);

        // S→C
        registrar.playToClient(
                EasterEggPacket.TYPE,
                EasterEggPacket.STREAM_CODEC,
                EasterEggPacket::handle);
        registrar.playToClient(
                SyncFrequencyListPacket.TYPE,
                SyncFrequencyListPacket.STREAM_CODEC,
                SyncFrequencyListPacket::handle);
        registrar.playToClient(
                SyncFrequencyDetailPacket.TYPE,
                SyncFrequencyDetailPacket.STREAM_CODEC,
                SyncFrequencyDetailPacket::handle);
        registrar.playToClient(
                UpdateFrequencyBasicPacket.TYPE,
                UpdateFrequencyBasicPacket.STREAM_CODEC,
                UpdateFrequencyBasicPacket::handle);
        registrar.playToClient(
                FrequencyResponsePacket.TYPE,
                FrequencyResponsePacket.STREAM_CODEC,
                FrequencyResponsePacket::handle);

        // Railgun: C→S
        registrar.playToServer(
                RailgunOpenGuiPacket.TYPE,
                RailgunOpenGuiPacket.STREAM_CODEC,
                RailgunOpenGuiPacket::handle);
        registrar.playToServer(
                RailgunBeamTogglePacket.TYPE,
                RailgunBeamTogglePacket.STREAM_CODEC,
                RailgunBeamTogglePacket::handle);
        registrar.playToServer(
                RailgunSettingsTogglePacket.TYPE,
                RailgunSettingsTogglePacket.STREAM_CODEC,
                RailgunSettingsTogglePacket::handle);

        // Railgun: S→C
        registrar.playToClient(
                RailgunFirePacket.TYPE,
                RailgunFirePacket.STREAM_CODEC,
                RailgunFirePacket::handle);
        registrar.playToClient(
                RailgunBeamUpdatePacket.TYPE,
                RailgunBeamUpdatePacket.STREAM_CODEC,
                RailgunBeamUpdatePacket::handle);
        registrar.playToClient(
                RailgunBeamChainFxPacket.TYPE,
                RailgunBeamChainFxPacket.STREAM_CODEC,
                RailgunBeamChainFxPacket::handle);
        registrar.playToClient(
                RailgunRecoilFxPacket.TYPE,
                RailgunRecoilFxPacket.STREAM_CODEC,
                RailgunRecoilFxPacket::handle);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }
}
