package com.moakiee.ae2lt.item;

import appeng.items.storage.BasicStorageCell;

import com.moakiee.ae2lt.me.key.LightningKeyType;

public final class LightningStorageCellItem extends BasicStorageCell {
    private static final int BYTES_PER_TYPE = 8;
    private static final int TOTAL_TYPES = 2;
    private static final double IDLE_DRAIN_PER_KILOBYTE = 256.0;

    public LightningStorageCellItem(int kilobytes) {
        super(
                new Properties().stacksTo(1),
                kilobytes * IDLE_DRAIN_PER_KILOBYTE,
                kilobytes,
                BYTES_PER_TYPE,
                TOTAL_TYPES,
                LightningKeyType.INSTANCE);
    }
}
