package com.moakiee.ae2lt.me.key;

import java.util.List;

import com.mojang.serialization.MapCodec;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.AE2LightningTech;

public final class LightningKey extends AEKey {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "lightning");
    public static final LightningKey INSTANCE = new LightningKey();
    public static final MapCodec<LightningKey> MAP_CODEC = MapCodec.unit(INSTANCE);

    private LightningKey() {
    }

    @Override
    public AEKeyType getType() {
        return LightningKeyType.INSTANCE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public Object getPrimaryKey() {
        return ID;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
    }

    @Override
    protected Component computeDisplayName() {
        return Component.translatable("key.ae2lt.lightning");
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LightningKey;
    }

    @Override
    public int hashCode() {
        return LightningKey.class.hashCode();
    }

    @Override
    public String toString() {
        return ID.toString();
    }
}
