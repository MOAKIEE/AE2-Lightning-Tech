package com.moakiee.ae2lt.config;

import net.minecraft.util.Mth;
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

    public static double lightningCollectorCrystalFeedRatio() {
        return VALUES.lightningCollectorCrystalFeedRatio.get();
    }

    public static double lightningCollectorOutputSpreadRatio() {
        return VALUES.lightningCollectorOutputSpreadRatio.get();
    }

    public static int lightningCollectorBaseMin(boolean extremeHighVoltage) {
        return extremeHighVoltage
                ? VALUES.lightningCollectorExtremeBaseMin.get()
                : VALUES.lightningCollectorHighBaseMin.get();
    }

    public static int lightningCollectorBaseMax(boolean extremeHighVoltage) {
        return extremeHighVoltage
                ? VALUES.lightningCollectorExtremeBaseMax.get()
                : VALUES.lightningCollectorHighBaseMax.get();
    }

    public static int lightningCollectorCrystalStart(boolean extremeHighVoltage) {
        return extremeHighVoltage
                ? VALUES.lightningCollectorExtremeCrystalStart.get()
                : VALUES.lightningCollectorHighCrystalStart.get();
    }

    public static int lightningCollectorCrystalEnd(boolean extremeHighVoltage) {
        return extremeHighVoltage
                ? VALUES.lightningCollectorExtremeCrystalEnd.get()
                : VALUES.lightningCollectorHighCrystalEnd.get();
    }

    public static int lightningCollectorPerfectOutput(boolean extremeHighVoltage) {
        return extremeHighVoltage
                ? VALUES.lightningCollectorExtremePerfectOutput.get()
                : VALUES.lightningCollectorHighPerfectOutput.get();
    }

    public static int electroChimeMaxCatalysis() {
        return VALUES.electroChimeMaxCatalysis.get();
    }

    public static int electroChimeStageThreshold(int stage) {
        int maxCatalysis = electroChimeMaxCatalysis();
        int stage1 = Mth.clamp(VALUES.electroChimeStage1Threshold.get(), 1, maxCatalysis);
        int stage2 = Mth.clamp(VALUES.electroChimeStage2Threshold.get(), stage1, maxCatalysis);
        int stage3 = Mth.clamp(VALUES.electroChimeStage3Threshold.get(), stage2, maxCatalysis);
        return switch (stage) {
            case 0 -> stage1;
            case 1 -> stage2;
            default -> stage3;
        };
    }

    public static int teslaCoilEnergyCapacity() {
        return VALUES.teslaCoilEnergyCapacity.get();
    }

    public static int teslaCoilMaxReceive() {
        return VALUES.teslaCoilMaxReceive.get();
    }

    public static int teslaCoilHighVoltageDustCost() {
        return VALUES.teslaCoilHighVoltageDustCost.get();
    }

    public static int teslaCoilHighVoltageEnergy() {
        return VALUES.teslaCoilHighVoltageEnergy.get();
    }

    public static int teslaCoilExtremeHighVoltageInput() {
        return VALUES.teslaCoilExtremeHighVoltageInput.get();
    }

    public static int teslaCoilExtremeHighVoltageEnergy() {
        return VALUES.teslaCoilExtremeHighVoltageEnergy.get();
    }

    private static final class Values {
        private final ModConfigSpec.IntValue lightningCollectorCooldownTicks;
        private final ModConfigSpec.DoubleValue lightningCollectorCrystalFeedRatio;
        private final ModConfigSpec.DoubleValue lightningCollectorOutputSpreadRatio;
        private final ModConfigSpec.IntValue lightningCollectorHighBaseMin;
        private final ModConfigSpec.IntValue lightningCollectorHighBaseMax;
        private final ModConfigSpec.IntValue lightningCollectorExtremeBaseMin;
        private final ModConfigSpec.IntValue lightningCollectorExtremeBaseMax;
        private final ModConfigSpec.IntValue lightningCollectorHighCrystalStart;
        private final ModConfigSpec.IntValue lightningCollectorHighCrystalEnd;
        private final ModConfigSpec.IntValue lightningCollectorExtremeCrystalStart;
        private final ModConfigSpec.IntValue lightningCollectorExtremeCrystalEnd;
        private final ModConfigSpec.IntValue lightningCollectorHighPerfectOutput;
        private final ModConfigSpec.IntValue lightningCollectorExtremePerfectOutput;
        private final ModConfigSpec.IntValue electroChimeMaxCatalysis;
        private final ModConfigSpec.IntValue electroChimeStage1Threshold;
        private final ModConfigSpec.IntValue electroChimeStage2Threshold;
        private final ModConfigSpec.IntValue electroChimeStage3Threshold;
        private final ModConfigSpec.IntValue teslaCoilEnergyCapacity;
        private final ModConfigSpec.IntValue teslaCoilMaxReceive;
        private final ModConfigSpec.IntValue teslaCoilHighVoltageDustCost;
        private final ModConfigSpec.IntValue teslaCoilHighVoltageEnergy;
        private final ModConfigSpec.IntValue teslaCoilExtremeHighVoltageInput;
        private final ModConfigSpec.IntValue teslaCoilExtremeHighVoltageEnergy;

        private Values(ModConfigSpec.Builder builder) {
            builder.push("lightningCollector");
            lightningCollectorCooldownTicks = builder
                    .comment("Cooldown in ticks after each captured lightning strike.")
                    .defineInRange("cooldownTicks", 100, 0, Integer.MAX_VALUE);
            lightningCollectorCrystalFeedRatio = builder
                    .comment("How much of an extreme high voltage strike is fed into a normal electro chime crystal.")
                    .defineInRange("crystalFeedRatio", 0.15D, 0.0D, 1.0D);
            lightningCollectorOutputSpreadRatio = builder
                    .comment("Random output spread ratio used by non-perfect electro chime crystals.")
                    .defineInRange("outputSpreadRatio", 0.12D, 0.0D, 1.0D);
            lightningCollectorHighBaseMin = builder.defineInRange("highVoltage.baseMin", 1, 1, Integer.MAX_VALUE);
            lightningCollectorHighBaseMax = builder.defineInRange("highVoltage.baseMax", 2, 1, Integer.MAX_VALUE);
            lightningCollectorExtremeBaseMin = builder.defineInRange("extremeHighVoltage.baseMin", 1, 1, Integer.MAX_VALUE);
            lightningCollectorExtremeBaseMax = builder.defineInRange("extremeHighVoltage.baseMax", 4, 1, Integer.MAX_VALUE);
            lightningCollectorHighCrystalStart = builder.defineInRange("highVoltage.crystalStart", 2, 1, Integer.MAX_VALUE);
            lightningCollectorHighCrystalEnd = builder.defineInRange("highVoltage.crystalEnd", 16, 1, Integer.MAX_VALUE);
            lightningCollectorExtremeCrystalStart = builder.defineInRange("extremeHighVoltage.crystalStart", 4, 1, Integer.MAX_VALUE);
            lightningCollectorExtremeCrystalEnd = builder.defineInRange("extremeHighVoltage.crystalEnd", 32, 1, Integer.MAX_VALUE);
            lightningCollectorHighPerfectOutput = builder.defineInRange("highVoltage.perfectOutput", 16, 1, Integer.MAX_VALUE);
            lightningCollectorExtremePerfectOutput = builder.defineInRange("extremeHighVoltage.perfectOutput", 32, 1, Integer.MAX_VALUE);
            builder.pop();

            builder.push("electroChimeCrystal");
            electroChimeMaxCatalysis = builder
                    .comment("Catalysis value needed to transform an electro chime crystal into its perfect form.")
                    .defineInRange("maxCatalysis", 256, 1, Integer.MAX_VALUE);
            electroChimeStage1Threshold = builder.defineInRange("stage1Threshold", 4, 0, Integer.MAX_VALUE);
            electroChimeStage2Threshold = builder.defineInRange("stage2Threshold", 16, 0, Integer.MAX_VALUE);
            electroChimeStage3Threshold = builder.defineInRange("stage3Threshold", 64, 0, Integer.MAX_VALUE);
            builder.pop();

            builder.push("teslaCoil");
            teslaCoilEnergyCapacity = builder
                    .comment("Tesla Coil internal FE buffer.")
                    .defineInRange("energyCapacity", 1_000_000, 1, Integer.MAX_VALUE);
            teslaCoilMaxReceive = builder
                    .comment("Tesla Coil max FE receive per transfer operation.")
                    .defineInRange("maxReceive", 200_000, 1, Integer.MAX_VALUE);
            teslaCoilHighVoltageDustCost = builder
                    .comment("Overload crystal dust required to synthesize one high voltage lightning.")
                    .defineInRange("highVoltage.dustCost", 4, 1, Integer.MAX_VALUE);
            teslaCoilHighVoltageEnergy = builder
                    .comment("FE required to synthesize one high voltage lightning.")
                    .defineInRange("highVoltage.energy", 50_000, 1, Integer.MAX_VALUE);
            teslaCoilExtremeHighVoltageInput = builder
                    .comment("High voltage lightning required to refine one extreme high voltage lightning.")
                    .defineInRange("extremeHighVoltage.highVoltageInput", 4, 1, Integer.MAX_VALUE);
            teslaCoilExtremeHighVoltageEnergy = builder
                    .comment("FE required to refine one extreme high voltage lightning.")
                    .defineInRange("extremeHighVoltage.energy", 500_000, 1, Integer.MAX_VALUE);
            builder.pop();
        }
    }
}
