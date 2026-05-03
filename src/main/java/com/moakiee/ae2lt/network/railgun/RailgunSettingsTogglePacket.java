package com.moakiee.ae2lt.network.railgun;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.menu.railgun.RailgunMenu;
import com.moakiee.ae2lt.network.NetworkInit;

/** Client to server: toggle GUI settings (terrain destruction / pvp lock). */
public record RailgunSettingsTogglePacket(RailgunSettings settings) implements CustomPacketPayload {

    public static final Type<RailgunSettingsTogglePacket> TYPE =
            new Type<>(NetworkInit.id("railgun_settings_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunSettingsTogglePacket> STREAM_CODEC =
            StreamCodec.composite(
                    RailgunSettings.STREAM_CODEC, RailgunSettingsTogglePacket::settings,
                    RailgunSettingsTogglePacket::new);

    @Override
    public Type<RailgunSettingsTogglePacket> type() {
        return TYPE;
    }

    public static void handle(RailgunSettingsTogglePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            if (p.containerMenu instanceof RailgunMenu menu) {
                menu.host().setSettings(pkt.settings());
            }
        });
    }
}
