package com.moakiee.ae2lt.item.railgun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public record RailgunSettings(boolean terrainDestruction, boolean pvpLock, BeamMode beamMode) {

    public static final RailgunSettings DEFAULT = new RailgunSettings(false, false, BeamMode.HV);

    public static final Codec<RailgunSettings> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.BOOL.fieldOf("terrain").forGetter(RailgunSettings::terrainDestruction),
            Codec.BOOL.fieldOf("pvp_lock").forGetter(RailgunSettings::pvpLock),
            BeamMode.CODEC.optionalFieldOf("beam_mode", BeamMode.HV).forGetter(RailgunSettings::beamMode))
            .apply(b, RailgunSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RailgunSettings::terrainDestruction,
            ByteBufCodecs.BOOL, RailgunSettings::pvpLock,
            BeamMode.STREAM_CODEC.cast(), RailgunSettings::beamMode,
            RailgunSettings::new);

    public RailgunSettings withTerrain(boolean v) {
        return new RailgunSettings(v, this.pvpLock, this.beamMode);
    }

    public RailgunSettings withPvpLock(boolean v) {
        return new RailgunSettings(this.terrainDestruction, v, this.beamMode);
    }

    public RailgunSettings withBeamMode(BeamMode mode) {
        return new RailgunSettings(this.terrainDestruction, this.pvpLock, mode);
    }

    /**
     * Beam ammunition mode. HV = basic DPS (no bypass), EHV = anti-armor (high bypass,
     * higher base damage, costs scarce EHV per settle). Charged-shot path is unaffected.
     */
    public enum BeamMode implements StringRepresentable {
        HV("hv"),
        EHV("ehv");

        public static final Codec<BeamMode> CODEC = StringRepresentable.fromEnum(BeamMode::values);
        public static final StreamCodec<ByteBuf, BeamMode> STREAM_CODEC =
                ByteBufCodecs.idMapper(BeamMode::byOrdinal, BeamMode::ordinal);

        private final String name;

        BeamMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public BeamMode next() {
            return this == HV ? EHV : HV;
        }

        public static BeamMode byOrdinal(int ordinal) {
            BeamMode[] vs = values();
            if (ordinal < 0 || ordinal >= vs.length) return HV;
            return vs[ordinal];
        }
    }
}
