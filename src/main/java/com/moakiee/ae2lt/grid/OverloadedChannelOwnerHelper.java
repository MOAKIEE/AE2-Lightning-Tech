package com.moakiee.ae2lt.grid;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.part.OverloadedCablePart;

import appeng.api.networking.IGridNode;

/**
 * Centralized owner checks for the 128-channel stage.
 * <p>
 * Important: this deliberately whitelists only the two AE2LT owner types that
 * should become 128-channel nodes in this stage.
 * Any new owner type must be added here deliberately, otherwise it stays on
 * vanilla AE2 limits by default.
 */
public final class OverloadedChannelOwnerHelper {
    private OverloadedChannelOwnerHelper() {
    }

    public static boolean is128ChannelOwner(@Nullable Object owner) {
        return owner instanceof OverloadedControllerBlockEntity
                || owner instanceof OverloadedCablePart;
    }

    public static boolean is128ChannelConnection(@Nullable Object ownerA, @Nullable Object ownerB) {
        // Only widen a connection if BOTH endpoints belong to AE2LT's overloaded
        // owners. This keeps mixed vanilla <-> overloaded links on vanilla limits.
        return is128ChannelOwner(ownerA) && is128ChannelOwner(ownerB);
    }

    public static @Nullable Object tryGetOwner(IGridNode node) {
        // AE2 1.21.1 exposes IGridNode#getOwner().
        // If your target AE2/MC version renames or relocates this method, adjust here.
        try {
            return node.getOwner();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
