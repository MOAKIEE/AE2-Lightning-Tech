package com.moakiee.ae2lt.machine.wireless;

import net.minecraft.network.chat.Component;

public enum WirelessStatus {
    UNCONNECTED("gui.ae2lt.wireless.status.unconnected", "gui.ae2lt.wireless.status.unconnected.desc"),
    WORKING("gui.ae2lt.wireless.status.working", "gui.ae2lt.wireless.status.working.desc"),
    REMOTE_ERROR("gui.ae2lt.wireless.status.remote_error", "gui.ae2lt.wireless.status.remote_error.desc"),
    NO_POWER("gui.ae2lt.wireless.status.no_power", "gui.ae2lt.wireless.status.no_power.desc");

    private final String translationKey;
    private final String descKey;

    WirelessStatus(String translationKey, String descKey) {
        this.translationKey = translationKey;
        this.descKey = descKey;
    }

    public Component getTranslation() {
        return Component.translatable(translationKey);
    }

    public Component getDesc() {
        return Component.translatable(descKey);
    }
}
