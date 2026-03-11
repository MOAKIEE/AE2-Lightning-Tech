package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.block.HighVoltageAggregatorBlock;
import com.moakiee.ae2lt.blockentity.HighVoltageAggregatorBlockEntity;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("ae2lt")
public class AE2LTJadePlugin implements IWailaPlugin {
    private static final HighVoltageAggregatorJadeProvider HIGH_VOLTAGE_AGGREGATOR_PROVIDER = new HighVoltageAggregatorJadeProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HIGH_VOLTAGE_AGGREGATOR_PROVIDER, HighVoltageAggregatorBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(HIGH_VOLTAGE_AGGREGATOR_PROVIDER, Block.class);
    }
}
