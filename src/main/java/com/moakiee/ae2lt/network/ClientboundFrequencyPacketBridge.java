package com.moakiee.ae2lt.network;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.UUID;

import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLEnvironment;

final class ClientboundFrequencyPacketBridge {
    private static final String CLIENT_HANDLER_CLASS =
            ClientboundFrequencyPacketBridge.class.getPackageName().replace(".network", ".client")
                    + ".ClientFrequencyPacketHandlers";

    private ClientboundFrequencyPacketBridge() {
    }

    static void syncFrequencyList(List<SyncFrequencyListPacket.FrequencyEntry> entries) {
        invokeClient("syncFrequencyList", new Class<?>[] { List.class }, entries);
    }

    static void syncFrequencyDetail(int frequencyId, byte syncType, CompoundTag data) {
        invokeClient("syncFrequencyDetail", new Class<?>[] { int.class, byte.class, CompoundTag.class },
                frequencyId, syncType, data);
    }

    static void updateFrequencyBasic(
            int frequencyId,
            boolean deleted,
            String name,
            int color,
            UUID ownerUUID,
            FrequencySecurityLevel security) {
        invokeClient("updateFrequencyBasic",
                new Class<?>[] {
                        int.class, boolean.class, String.class, int.class, UUID.class, FrequencySecurityLevel.class
                },
                frequencyId, deleted, name, color, ownerUUID, security);
    }

    static void frequencyResponse(Component message) {
        invokeClient("frequencyResponse", new Class<?>[] { Component.class }, message);
    }

    private static void invokeClient(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (!FMLEnvironment.getDist().isClient()) {
            return;
        }

        try {
            Class<?> handlerClass = Class.forName(CLIENT_HANDLER_CLASS);
            handlerClass.getMethod(methodName, parameterTypes).invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Client frequency packet handler failed: " + methodName, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing client frequency packet handler: " + methodName, e);
        }
    }
}
