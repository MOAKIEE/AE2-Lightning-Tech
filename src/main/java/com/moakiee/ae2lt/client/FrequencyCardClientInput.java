package com.moakiee.ae2lt.client;

import net.minecraft.client.Minecraft;

public final class FrequencyCardClientInput {
    private FrequencyCardClientInput() {
    }

    public static boolean hasShiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }
}
