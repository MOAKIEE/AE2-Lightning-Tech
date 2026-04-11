package com.moakiee.ae2lt.grid;

import java.util.Collections;
import java.util.Set;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import appeng.api.networking.IGridNode;

/**
 * Centralized owner checks for the overloaded-channel subsystem.
 * Any grid-node owner implementing {@link OverloadedGridNodeOwner} is
 * automatically granted elevated channel capacity by the AE2LT mixins.
 */
public final class OverloadedChannelOwnerHelper {
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

    public static int channelsPerController() {
        return AE2LTCommonConfig.overloadedControllerChannelsPerController();
    }

    public static int supplyPerController(int cableCapacityFactor) {
        long supply = (long) channelsPerController() * Math.max(1, cableCapacityFactor);
        return (int) Math.min(Integer.MAX_VALUE / 2, supply);
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
}
