package com.moakiee.ae2lt.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AE2LTCommonConfig {
    public static final ModConfigSpec SPEC;

    private static final Values VALUES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        VALUES = new Values(builder);
        SPEC = builder.build();
    }

    private AE2LTCommonConfig() {
    }

    public static int lightningCollectorCooldownTicks() {
        return VALUES.lightningCollectorCooldownTicks.get();
    }

    public static int electroChimeMaxCatalysis() {
        return VALUES.electroChimeMaxCatalysis.get();
    }

    private static final class Values {
        private final ModConfigSpec.IntValue lightningCollectorCooldownTicks;
        private final ModConfigSpec.IntValue electroChimeMaxCatalysis;

        private Values(ModConfigSpec.Builder builder) {
            builder.push("lightningCollector");
            lightningCollectorCooldownTicks = builder
                    .comment("Cooldown in ticks after each captured lightning strike.")
                    .defineInRange("cooldownTicks", 100, 0, Integer.MAX_VALUE);
            builder.pop();

            builder.push("electroChimeCrystal");
            electroChimeMaxCatalysis = builder
                    .comment("Catalysis value needed to transform an electro chime crystal into its perfect form.")
                    .defineInRange("maxCatalysis", 256, 1, Integer.MAX_VALUE);
            builder.pop();
        }
    }
}
