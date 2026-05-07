package com.moakiee.ae2lt.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record EasterEggPacket() {
    public static EasterEggPacket decode(FriendlyByteBuf buf) {
        return new EasterEggPacket();
    }

    public static void encode(EasterEggPacket payload, FriendlyByteBuf buf) {
    }

    public static void handle(EasterEggPacket payload, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            com.moakiee.ae2lt.client.EasterEggOverlay.trigger();
        });
        ctx.setPacketHandled(true);
    }
}

