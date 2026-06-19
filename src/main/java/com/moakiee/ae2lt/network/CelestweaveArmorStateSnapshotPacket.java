package com.moakiee.ae2lt.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

public record CelestweaveArmorStateSnapshotPacket(
        UUID armorId,
        Map<String, Boolean> submoduleActiveStates,
        boolean flightInertiaEnabled)
        implements CustomPacketPayload {

    private static final int MAX_SUBMODULE_STATES = 64;

    public static final Type<CelestweaveArmorStateSnapshotPacket> TYPE =
            new Type<>(NetworkInit.id("celestweave_armor_state_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CelestweaveArmorStateSnapshotPacket> STREAM_CODEC =
            StreamCodec.ofMember(
                    CelestweaveArmorStateSnapshotPacket::write,
                    CelestweaveArmorStateSnapshotPacket::decode);

    public CelestweaveArmorStateSnapshotPacket {
        submoduleActiveStates = submoduleActiveStates == null ? Map.of() : Map.copyOf(submoduleActiveStates);
    }

    @Override
    public Type<CelestweaveArmorStateSnapshotPacket> type() {
        return TYPE;
    }

    public static CelestweaveArmorStateSnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        UUID armorId = buf.readUUID();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_SUBMODULE_STATES) {
            throw new IllegalArgumentException("Invalid Celestweave state snapshot size: " + size);
        }
        Map<String, Boolean> activeStates = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            activeStates.put(buf.readUtf(128), buf.readBoolean());
        }
        return new CelestweaveArmorStateSnapshotPacket(
                armorId,
                activeStates,
                buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeVarInt(submoduleActiveStates.size());
        submoduleActiveStates.forEach((submoduleId, active) -> {
            buf.writeUtf(submoduleId, 128);
            buf.writeBoolean(Boolean.TRUE.equals(active));
        });
        buf.writeBoolean(flightInertiaEnabled);
    }

    public static void handle(CelestweaveArmorStateSnapshotPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> CelestweaveArmorState.applyClientStateSnapshot(
                payload.armorId(),
                payload.submoduleActiveStates(),
                payload.flightInertiaEnabled()));
    }
}
