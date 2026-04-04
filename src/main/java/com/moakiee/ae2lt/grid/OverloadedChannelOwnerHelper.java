package com.moakiee.ae2lt.grid;

import java.util.Collections;
import java.util.Set;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

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
    public static final int CHANNELS_PER_CONTROLLER = 128;
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final Set<String> LOGGED_OWNER_LOOKUP_FAILURES =
            Collections.synchronizedSet(new java.util.HashSet<>());

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
        } catch (RuntimeException exception) {
            String key = node.getClass().getName() + ":" + exception.getClass().getName();
            if (LOGGED_OWNER_LOOKUP_FAILURES.add(key)) {
                LOG.warn("AE2LT failed to read grid node owner from {}.", node.getClass().getName(), exception);
            }
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
