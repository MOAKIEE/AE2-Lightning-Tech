package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkInit {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AE2LightningTech.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private NetworkInit() {
    }

    public static void register() {
        // C→S
        CHANNEL.registerMessage(packetId++, WirelessConnectorUsePacket.class,
                WirelessConnectorUsePacket::encode, WirelessConnectorUsePacket::decode, WirelessConnectorUsePacket::handle);
        CHANNEL.registerMessage(packetId++, OpenFrequencyMenuPacket.class,
                OpenFrequencyMenuPacket::encode, OpenFrequencyMenuPacket::decode, OpenFrequencyMenuPacket::handle);

        // frequency system: C→S
        CHANNEL.registerMessage(packetId++, CreateFrequencyPacket.class,
                CreateFrequencyPacket::encode, CreateFrequencyPacket::decode, CreateFrequencyPacket::handle);
        CHANNEL.registerMessage(packetId++, DeleteFrequencyPacket.class,
                DeleteFrequencyPacket::encode, DeleteFrequencyPacket::decode, DeleteFrequencyPacket::handle);
        CHANNEL.registerMessage(packetId++, EditFrequencyPacket.class,
                EditFrequencyPacket::encode, EditFrequencyPacket::decode, EditFrequencyPacket::handle);
        CHANNEL.registerMessage(packetId++, SelectFrequencyPacket.class,
                SelectFrequencyPacket::encode, SelectFrequencyPacket::decode, SelectFrequencyPacket::handle);
        CHANNEL.registerMessage(packetId++, ChangeMemberPacket.class,
                ChangeMemberPacket::encode, ChangeMemberPacket::decode, ChangeMemberPacket::handle);

        // S→C
        CHANNEL.registerMessage(packetId++, EasterEggPacket.class,
                EasterEggPacket::encode, EasterEggPacket::decode, EasterEggPacket::handle);
        CHANNEL.registerMessage(packetId++, SyncFrequencyListPacket.class,
                SyncFrequencyListPacket::encode, SyncFrequencyListPacket::decode, SyncFrequencyListPacket::handle);
        CHANNEL.registerMessage(packetId++, SyncFrequencyDetailPacket.class,
                SyncFrequencyDetailPacket::encode, SyncFrequencyDetailPacket::decode, SyncFrequencyDetailPacket::handle);
        CHANNEL.registerMessage(packetId++, UpdateFrequencyBasicPacket.class,
                UpdateFrequencyBasicPacket::encode, UpdateFrequencyBasicPacket::decode, UpdateFrequencyBasicPacket::handle);
        CHANNEL.registerMessage(packetId++, FrequencyResponsePacket.class,
                FrequencyResponsePacket::encode, FrequencyResponsePacket::decode, FrequencyResponsePacket::handle);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(AE2LightningTech.MODID, path);
    }
}
