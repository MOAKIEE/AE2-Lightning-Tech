package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class LightningCollectorJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "lightning_collector");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof LightningCollectorBlockEntity collector) {
            data.putInt("CooldownTicks", collector.getCooldownTicks());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getBlockState().is(ModBlocks.LIGHTNING_COLLECTOR.get())) {
            return;
        }

        CompoundTag data = accessor.getServerData();
        tooltip.add(Component.translatable(
                "jade.ae2lt.lightning_collector.cooldown",
                String.format("%.1f", Math.max(0, data.getInt("CooldownTicks")) / 20.0D)));
    }
}