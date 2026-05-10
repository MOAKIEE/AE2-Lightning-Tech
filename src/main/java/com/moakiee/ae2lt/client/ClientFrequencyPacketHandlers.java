package com.moakiee.ae2lt.client;

import java.util.List;
import java.util.UUID;

import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.network.SyncFrequencyDetailPacket;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class ClientFrequencyPacketHandlers {
    private ClientFrequencyPacketHandlers() {
    }

    public static void syncFrequencyList(List<SyncFrequencyListPacket.FrequencyEntry> entries) {
        ClientFrequencyCache.updateFromSync(entries);
    }

    public static void syncFrequencyDetail(int frequencyId, byte syncType, CompoundTag data) {
        if (syncType == SyncFrequencyDetailPacket.TYPE_MEMBERS) {
            ClientFrequencyCache.updateMembers(frequencyId, data);
        } else if (syncType == SyncFrequencyDetailPacket.TYPE_CONNECTIONS) {
            ClientFrequencyCache.updateConnections(frequencyId, data);
        }
    }

    public static void updateFrequencyBasic(
            int frequencyId,
            boolean deleted,
            String name,
            int color,
            UUID ownerUUID,
            FrequencySecurityLevel security) {
        if (deleted) {
            ClientFrequencyCache.removeFrequency(frequencyId);
        } else {
            ClientFrequencyCache.upsertFrequency(frequencyId, name, color, ownerUUID, security);
        }
    }

    public static void frequencyResponse(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        // Container screens cover the action-bar area, so show the error in
        // the frequency GUI when possible and use the overlay only as fallback.
        if (minecraft.screen instanceof FrequencyScreen frequencyScreen) {
            frequencyScreen.showInlineError(message);
        } else {
            minecraft.player.sendOverlayMessage(message);
        }
    }
}
