package com.moakiee.ae2lt.grid;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.blockentity.networking.ControllerBlockEntity;

/**
 * Centralized owner checks for the overloaded-channel subsystem.
 * Any grid-node owner implementing {@link OverloadedGridNodeOwner} is
 * automatically granted elevated channel capacity by the AE2LT mixins.
 */
public final class OverloadedChannelOwnerHelper {
    public static final int CHANNELS_PER_CONTROLLER = 8;

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

    /**
     * @return total channel capacity for an overloaded-controller network,
     *         or 0 if no overloaded controllers are present / channel mode is INFINITE.
     */
    public static int calculateOverloadedNetworkCapacity(IGrid grid) {
        int overloadedCount = 0;
        for (var node : grid.getMachineNodes(ControllerBlockEntity.class)) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) {
                overloadedCount++;
            }
        }
        if (overloadedCount == 0) {
            return 0;
        }

        var channelMode = grid.getPathingService().getChannelMode();
        if (channelMode == ChannelMode.INFINITE) {
            return 0;
        }

        return CHANNELS_PER_CONTROLLER * overloadedCount * channelMode.getCableCapacityFactor();
    }
}
