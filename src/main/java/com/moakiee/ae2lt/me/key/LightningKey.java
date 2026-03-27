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
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.AE2LightningTech;

public final class LightningKey extends AEKey {
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "lightning");
    public static final ResourceLocation ID = TYPE_ID;
    public static final ResourceLocation HIGH_VOLTAGE_ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "high_voltage_lightning");
    public static final ResourceLocation EXTREME_HIGH_VOLTAGE_ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "extreme_high_voltage_lightning");

    public static final LightningKey HIGH_VOLTAGE = new LightningKey(Tier.HIGH_VOLTAGE);
    public static final LightningKey EXTREME_HIGH_VOLTAGE = new LightningKey(Tier.EXTREME_HIGH_VOLTAGE);
        public static final MapCodec<LightningKey> MAP_CODEC =
            Tier.CODEC.optionalFieldOf("tier", Tier.HIGH_VOLTAGE)
                .xmap(LightningKey::of, LightningKey::tier);

    private final Tier tier;

    public enum Tier implements StringRepresentable {
        HIGH_VOLTAGE("high_voltage"),
        EXTREME_HIGH_VOLTAGE("extreme_high_voltage");

        public static final com.mojang.serialization.Codec<Tier> CODEC = StringRepresentable.fromEnum(Tier::values);

        private final String serializedName;

        Tier(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }

        public static Tier fromOrdinal(int ordinal) {
            return switch (ordinal) {
                case 1 -> EXTREME_HIGH_VOLTAGE;
                default -> HIGH_VOLTAGE;
            };
        }
    }

    private LightningKey(Tier tier) {
        this.tier = tier;
    }

    public static LightningKey of(Tier tier) {
        return tier == Tier.EXTREME_HIGH_VOLTAGE ? EXTREME_HIGH_VOLTAGE : HIGH_VOLTAGE;
    }

    public static LightningKey fromOrdinal(int ordinal) {
        return of(Tier.fromOrdinal(ordinal));
    }

    public Tier tier() {
        return this.tier;
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
        CompoundTag tag = new CompoundTag();
        tag.putString("tier", this.tier.getSerializedName());
        return tag;
    }

    @Override
    public Object getPrimaryKey() {
        return this.tier;
    }

    @Override
    public ResourceLocation getId() {
        return this.tier == Tier.EXTREME_HIGH_VOLTAGE ? EXTREME_HIGH_VOLTAGE_ID : HIGH_VOLTAGE_ID;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeByte(this.tier.ordinal());
    }

    @Override
    protected Component computeDisplayName() {
        return this.tier == Tier.EXTREME_HIGH_VOLTAGE
                ? Component.translatable("key.ae2lt.extreme_high_voltage_lightning")
                : Component.translatable("key.ae2lt.high_voltage_lightning");
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
        return obj instanceof LightningKey other && this.tier == other.tier;
    }

    @Override
    public int hashCode() {
        return this.tier.hashCode();
    }

    @Override
    public String toString() {
        return getId().toString();
    }
}
