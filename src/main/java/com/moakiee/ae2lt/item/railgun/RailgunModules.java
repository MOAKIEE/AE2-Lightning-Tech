package com.moakiee.ae2lt.item.railgun;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Persisted per-stack railgun module configuration. Each slot maps to one module
 * item; lists allow up to 2 of compute/acceleration. Empty slots are missing entries.
 *
 * <p>Stored as ItemStack so each module instance can carry its own NBT/components in
 * the future without schema migration.
 */
public record RailgunModules(
        ItemStack core,
        List<ItemStack> compute,
        ItemStack resonance,
        List<ItemStack> acceleration,
        ItemStack energy) {

    public static final int MAX_COMPUTE = 2;
    public static final int MAX_ACCELERATION = 2;

    public static final RailgunModules EMPTY = new RailgunModules(
            ItemStack.EMPTY,
            List.of(),
            ItemStack.EMPTY,
            List.of(),
            ItemStack.EMPTY);

    public static final Codec<RailgunModules> CODEC = RecordCodecBuilder.create(b -> b.group(
            ItemStack.OPTIONAL_CODEC.fieldOf("core").forGetter(RailgunModules::core),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("compute").forGetter(RailgunModules::compute),
            ItemStack.OPTIONAL_CODEC.fieldOf("resonance").forGetter(RailgunModules::resonance),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("acceleration").forGetter(RailgunModules::acceleration),
            ItemStack.OPTIONAL_CODEC.fieldOf("energy").forGetter(RailgunModules::energy))
            .apply(b, RailgunModules::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunModules> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, RailgunModules::core,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), RailgunModules::compute,
            ItemStack.OPTIONAL_STREAM_CODEC, RailgunModules::resonance,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), RailgunModules::acceleration,
            ItemStack.OPTIONAL_STREAM_CODEC, RailgunModules::energy,
            RailgunModules::new);

    public boolean hasCore() {
        return !core.isEmpty();
    }

    public boolean hasResonance() {
        return !resonance.isEmpty();
    }

    public boolean hasEnergy() {
        return !energy.isEmpty();
    }

    public int computeCount() {
        int n = 0;
        for (ItemStack s : compute) {
            if (!s.isEmpty()) n++;
        }
        return Math.min(n, MAX_COMPUTE);
    }

    public int accelerationCount() {
        int n = 0;
        for (ItemStack s : acceleration) {
            if (!s.isEmpty()) n++;
        }
        return Math.min(n, MAX_ACCELERATION);
    }
}
