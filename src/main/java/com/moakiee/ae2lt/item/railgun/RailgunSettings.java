package com.moakiee.ae2lt.item.railgun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record RailgunSettings(boolean terrainDestruction, boolean pvpLock) {

    public static final RailgunSettings DEFAULT = new RailgunSettings(false, false);

    public static final Codec<RailgunSettings> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.BOOL.fieldOf("terrain").forGetter(RailgunSettings::terrainDestruction),
            Codec.BOOL.fieldOf("pvp_lock").forGetter(RailgunSettings::pvpLock))
            .apply(b, RailgunSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RailgunSettings::terrainDestruction,
            ByteBufCodecs.BOOL, RailgunSettings::pvpLock,
            RailgunSettings::new);

    public RailgunSettings withTerrain(boolean v) {
        return new RailgunSettings(v, this.pvpLock);
    }

    public RailgunSettings withPvpLock(boolean v) {
        return new RailgunSettings(this.terrainDestruction, v);
    }
}
