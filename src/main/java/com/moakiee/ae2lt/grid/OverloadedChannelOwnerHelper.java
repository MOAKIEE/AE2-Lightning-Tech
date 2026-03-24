package com.moakiee.ae2lt.grid;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridNode;

/**
 * Centralized owner checks for the 128-channel stage.
 * Any grid-node owner implementing {@link OverloadedGridNodeOwner} is
 * automatically granted 128-channel capacity by the AE2LT mixins.
 */
public final class OverloadedChannelOwnerHelper {
    private OverloadedChannelOwnerHelper() {
    }

    public static boolean is128ChannelOwner(@Nullable Object owner) {
        return owner instanceof OverloadedGridNodeOwner;
    }

    public static boolean is128ChannelConnection(@Nullable Object ownerA, @Nullable Object ownerB) {
        return is128ChannelOwner(ownerA) && is128ChannelOwner(ownerB);
    }

    public static @Nullable Object tryGetOwner(IGridNode node) {
        try {
            return node.getOwner();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
