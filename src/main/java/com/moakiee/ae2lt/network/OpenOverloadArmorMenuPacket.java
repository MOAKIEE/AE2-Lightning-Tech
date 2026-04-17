package com.moakiee.ae2lt.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.item.OverloadArmorItem;

public record OpenOverloadArmorMenuPacket() implements CustomPacketPayload {
    public static final Type<OpenOverloadArmorMenuPacket> TYPE =
            new Type<>(NetworkInit.id("open_overload_armor_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOverloadArmorMenuPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenOverloadArmorMenuPacket::write, OpenOverloadArmorMenuPacket::decode);

    @Override
    public Type<OpenOverloadArmorMenuPacket> type() {
        return TYPE;
    }

    public static OpenOverloadArmorMenuPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenOverloadArmorMenuPacket();
    }

    public void write(RegistryFriendlyByteBuf buf) {
    }

    public static void handle(OpenOverloadArmorMenuPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                payload.handleOnServer(player);
            }
        });
    }

    private void handleOnServer(ServerPlayer player) {
        if (!OverloadArmorItem.openEquippedMenu(player)) {
            player.displayClientMessage(Component.translatable("ae2lt.overload_armor.not_equipped"), true);
        }
    }
}
