package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

public class LightningCollectorJadeProvider implements StreamServerDataProvider<BlockAccessor, Integer> {
    public static final LightningCollectorJadeProvider INSTANCE = new LightningCollectorJadeProvider();

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "lightning_collector");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        StreamServerDataProvider.super.appendServerData(data, accessor);
    }

    @Override
    public Integer streamData(BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof LightningCollectorBlockEntity collector) {
            return collector.getCooldownTicks();
        }
        return null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Integer> streamCodec() {
        return ByteBufCodecs.VAR_INT.cast();
    }

    public static final class Client implements IBlockComponentProvider {
        public static final Client INSTANCE = new Client();

        private Client() {
        }

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getBlockState().is(ModBlocks.LIGHTNING_COLLECTOR.get())) {
                return;
            }

            int cooldownTicks = LightningCollectorJadeProvider.INSTANCE.decodeFromData(accessor).orElse(0);
            if (cooldownTicks > 0) {
                int cooldownSeconds = Math.max(1, (cooldownTicks + 19) / 20);
                tooltip.add(Component.translatable("jade.ae2lt.lightning_collector.cooldown", cooldownSeconds));
            }
        }
    }
}
