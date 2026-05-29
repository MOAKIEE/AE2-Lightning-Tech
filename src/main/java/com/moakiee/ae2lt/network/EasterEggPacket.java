package com.moakiee.ae2lt.network;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class EasterEggPacket {

    public EasterEggPacket() {
    }

    public static void encode(EasterEggPacket pkt, FriendlyByteBuf buf) {
        // no fields
    }

    public static EasterEggPacket decode(FriendlyByteBuf buf) {
        return new EasterEggPacket();
    }

    public static void handle(EasterEggPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            com.moakiee.ae2lt.client.EasterEggOverlay.trigger();
        });
        ctx.setPacketHandled(true);
    }
}
