package com.moakiee.ae2lt.grid.wirelesslink;

public enum WirelessLinkState {
    CONNECTED(false),
    PENDING_TARGET_CHUNK(false),
    PENDING_TRANSMITTER(false),
    TARGET_NOT_READY(false),
    FREQUENCY_INVALID(false),
    PERMISSION_DENIED(false),
    TARGET_MISSING(true),
    TARGET_TYPE_CHANGED(true),
    TARGET_NOT_NETWORK_DEVICE(true),
    PART_MISSING(true),
    PART_TYPE_CHANGED(true),
    PART_NOT_NETWORK_DEVICE(true),
    REDUNDANT_LINK(true),
    DISCONNECTED(true),
    REMOVED(true);

    private final boolean cleanupCandidate;

    WirelessLinkState(boolean cleanupCandidate) {
        this.cleanupCandidate = cleanupCandidate;
    }

    public boolean isCleanupCandidate() {
        return cleanupCandidate;
    }
}
