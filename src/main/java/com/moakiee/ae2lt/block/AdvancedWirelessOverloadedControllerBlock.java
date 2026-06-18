package com.moakiee.ae2lt.block;

import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * Block for the Advanced Wireless Overloaded Controller.
 * Shares the frequency GUI with the base controller; binds to
 * {@link com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity}.
 */
public class AdvancedWirelessOverloadedControllerBlock extends WirelessOverloadedControllerBlock {
    public AdvancedWirelessOverloadedControllerBlock(Properties properties) {
        super(properties);
    }

    public AdvancedWirelessOverloadedControllerBlock() {
        super();
    }
}
