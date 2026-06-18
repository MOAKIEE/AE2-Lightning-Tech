package com.moakiee.ae2lt.logic;

public final class WirelessImportCacheCursor {
    private WirelessImportCacheCursor() {
    }

    /**
     * Calculate next cursor position after a scan. When the entire cache is
     * visited, advance by 1 to rotate fairly across all keys; otherwise
     * advance by visited count to resume where the scan stopped.
     */
    public static int nextIndex(int startIndex, int size, int visited) {
        if (size <= 0) {
            return 0;
        }
        int start = Math.floorMod(startIndex, size);
        int step = visited >= size ? 1 : Math.max(visited, 1);
        return (start + step) % size;
    }
}
