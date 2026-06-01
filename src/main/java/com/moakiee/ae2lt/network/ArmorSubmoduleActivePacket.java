package com.moakiee.ae2lt.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public record ArmorSubmoduleActivePacket(UUID armorId, String submoduleId, boolean active)
        implements CustomPacketPayload {

    public static final Type<ArmorSubmoduleActivePacket> TYPE =
            new Type<>(NetworkInit.id("armor_submodule_active"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorSubmoduleActivePacket> STREAM_CODEC =
            StreamCodec.ofMember(ArmorSubmoduleActivePacket::write, ArmorSubmoduleActivePacket::decode);

    @Override
    public Type<ArmorSubmoduleActivePacket> type() {
        return TYPE;
    }

    public static ArmorSubmoduleActivePacket decode(RegistryFriendlyByteBuf buf) {
        return new ArmorSubmoduleActivePacket(
                buf.readUUID(),
                buf.readUtf(128),
                buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeUtf(submoduleId, 128);
        buf.writeBoolean(active);
    }

    public static void handle(ArmorSubmoduleActivePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> OverloadArmorState.markClientActive(
                payload.armorId(),
                payload.submoduleId(),
                payload.active()));
    }
}
