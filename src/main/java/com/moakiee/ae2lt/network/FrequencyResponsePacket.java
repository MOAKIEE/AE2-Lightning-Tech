package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record FrequencyResponsePacket(int responseCode) {

    public static final int REQUIRE_PASSWORD = 1;
    public static final int NO_PERMISSION = 2;
    public static final int INVALID_FREQUENCY = 3;
    public static final int REJECTED = 4;
    public static final int FREQUENCY_IN_USE = 5;

    public static void encode(FrequencyResponsePacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.responseCode);
    }

    public static FrequencyResponsePacket decode(FriendlyByteBuf buf) {
        return new FrequencyResponsePacket(buf.readInt());
    }

    private Component toMessage() {
        return switch (responseCode) {
            case REQUIRE_PASSWORD -> Component.translatable("ae2lt.gui.error.require_password");
            case NO_PERMISSION -> Component.translatable("ae2lt.gui.error.no_permission");
            case INVALID_FREQUENCY -> Component.translatable("ae2lt.gui.error.invalid_frequency");
            case FREQUENCY_IN_USE -> Component.translatable("ae2lt.gui.error.frequency_in_use");
            default -> Component.translatable("ae2lt.gui.error.rejected");
        };
    }

    public static void handle(FrequencyResponsePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null) return;
            Component message = pkt.toMessage();
            // Container screens cover the hotbar / action-bar region, so
            // a stock {@code displayClientMessage(..., true)} is painted
            // underneath the GUI and the player never sees it. Route
            // the toast into the FrequencyScreen's inline banner when
            // it's open, and fall back to the action-bar only when it
            // isn't (e.g. an error arrives after the user closed the
            // GUI). Chat stays untouched either way.
            if (minecraft.screen instanceof FrequencyScreen fs) {
                fs.showInlineError(message);
            } else {
                player.displayClientMessage(message, true);
            }
        });
        ctx.setPacketHandled(true);
    }
}

