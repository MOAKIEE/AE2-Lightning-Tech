package com.moakiee.ae2lt.item;

import appeng.items.storage.BasicStorageCell;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.moakiee.ae2lt.me.key.LightningKeyType;

public final class LightningStorageComponentItem extends BasicStorageCell {
    private static final int BYTES_PER_TYPE = 8;
    private static final int TOTAL_TYPES = 2;
    private final int usableCapacity;
    private final int totalBytes;

    public LightningStorageComponentItem(int usableCapacity, double idleDrain) {
        super(
                new Properties().stacksTo(1),
                Items.IRON_INGOT,  // coreItem placeholder
                Items.IRON_INGOT,  // housingItem placeholder
                idleDrain,
                usableCapacity + BYTES_PER_TYPE * TOTAL_TYPES,
                BYTES_PER_TYPE,
                TOTAL_TYPES,
                LightningKeyType.INSTANCE);
        this.usableCapacity = usableCapacity;
        if ((usableCapacity & 7) != 0) {
            throw new IllegalArgumentException(
                    "Lightning storage component capacity must be a multiple of 8: " + usableCapacity);
        }
        this.totalBytes = usableCapacity + BYTES_PER_TYPE * TOTAL_TYPES;
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return this.totalBytes;
    }

    public int getUsableCapacity() {
        return usableCapacity;
    }
}
