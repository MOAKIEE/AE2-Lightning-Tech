package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.blockentity.HighVoltageAggregatorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class HighVoltageAggregatorJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "high_voltage_aggregator");
    private static final String HIGH_TICKS_KEY = "HighVoltageTicks";
    private static final String EXTREME_TICKS_KEY = "ExtremeHighVoltageTicks";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof HighVoltageAggregatorBlockEntity aggregator) {
            data.putInt(HIGH_TICKS_KEY, aggregator.getHighVoltageTicks());
            data.putInt(EXTREME_TICKS_KEY, aggregator.getExtremeHighVoltageTicks());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getBlockState().is(ModBlocks.HIGH_VOLTAGE_AGGREGATOR.get())) {
            return;
        }

        CompoundTag data = accessor.getServerData();
        int highTicks = Math.max(0, data.getInt(HIGH_TICKS_KEY));
        int remainingTicks = Math.max(0, data.getInt(EXTREME_TICKS_KEY));
        if ((!data.contains(HIGH_TICKS_KEY) || !data.contains(EXTREME_TICKS_KEY))
                && accessor.getBlockEntity() instanceof HighVoltageAggregatorBlockEntity aggregator) {
            highTicks = Math.max(0, aggregator.getHighVoltageTicks());
            remainingTicks = Math.max(0, aggregator.getExtremeHighVoltageTicks());
        }

        String modeKey = "jade.ae2lt.high_voltage_aggregator.mode.standby";
        if (remainingTicks > 0) {
            modeKey = "jade.ae2lt.high_voltage_aggregator.mode.extreme";
        } else if (highTicks > 0) {
            modeKey = "jade.ae2lt.high_voltage_aggregator.mode.high";
            remainingTicks = highTicks;
        }

        tooltip.add(Component.translatable(
                "jade.ae2lt.high_voltage_aggregator.mode",
                Component.translatable(modeKey)));
        tooltip.add(Component.translatable(
                "jade.ae2lt.high_voltage_aggregator.duration",
                String.format("%.1f", remainingTicks / 20.0D)));
    }
}
