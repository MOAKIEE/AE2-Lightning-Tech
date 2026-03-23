package com.moakiee.ae2lt.compat.extae;

import com.glodblock.github.extendedae.common.blocks.BlockWirelessConnector;

/**
 * Overloaded variant of ExtendedAE's Wireless Connector.
 * Inherits all block behaviour (GUI, state updates, neighbour handling)
 * from the original; the only functional change lives in the block entity
 * which is marked as an {@link com.moakiee.ae2lt.grid.OverloadedGridNodeOwner}
 * for 128-channel support.
 */
public class OverloadedWirelessConnectorBlock extends BlockWirelessConnector {

    public OverloadedWirelessConnectorBlock() {
        super();
    }
}
