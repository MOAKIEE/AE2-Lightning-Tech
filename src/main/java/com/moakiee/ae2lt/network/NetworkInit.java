package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkInit {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextPacketId;
    private static boolean registered;

    private NetworkInit() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.registerMessage(
                nextPacketId++,
                WirelessConnectorUsePacket.class,
                WirelessConnectorUsePacket::encode,
                WirelessConnectorUsePacket::decode,
                WirelessConnectorUsePacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenFrequencyMenuPacket.class,
                OpenFrequencyMenuPacket::encode,
                OpenFrequencyMenuPacket::decode,
                OpenFrequencyMenuPacket::handle);

        // frequency system: C→S
        CHANNEL.registerMessage(
                nextPacketId++,
                CreateFrequencyPacket.class,
                CreateFrequencyPacket::encode,
                CreateFrequencyPacket::decode,
                CreateFrequencyPacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                DeleteFrequencyPacket.class,
                DeleteFrequencyPacket::encode,
                DeleteFrequencyPacket::decode,
                DeleteFrequencyPacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                EditFrequencyPacket.class,
                EditFrequencyPacket::encode,
                EditFrequencyPacket::decode,
                EditFrequencyPacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                SelectFrequencyPacket.class,
                SelectFrequencyPacket::encode,
                SelectFrequencyPacket::decode,
                SelectFrequencyPacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                ChangeMemberPacket.class,
                ChangeMemberPacket::encode,
                ChangeMemberPacket::decode,
                ChangeMemberPacket::handle);

        // S→C
        CHANNEL.registerMessage(
                nextPacketId++,
                EasterEggPacket.class,
                EasterEggPacket::encode,
                EasterEggPacket::decode,
                EasterEggPacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                SyncFrequencyListPacket.class,
                SyncFrequencyListPacket::encode,
                SyncFrequencyListPacket::decode,
                SyncFrequencyListPacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                SyncFrequencyDetailPacket.class,
                SyncFrequencyDetailPacket::encode,
                SyncFrequencyDetailPacket::decode,
                SyncFrequencyDetailPacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                UpdateFrequencyBasicPacket.class,
                UpdateFrequencyBasicPacket::encode,
                UpdateFrequencyBasicPacket::decode,
                UpdateFrequencyBasicPacket::handle);
        CHANNEL.registerMessage(
                nextPacketId++,
                FrequencyResponsePacket.class,
                FrequencyResponsePacket::encode,
                FrequencyResponsePacket::decode,
                FrequencyResponsePacket::handle);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(AE2LightningTech.MODID, path);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}

