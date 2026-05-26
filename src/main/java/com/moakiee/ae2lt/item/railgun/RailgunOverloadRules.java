package com.moakiee.ae2lt.item.railgun;

public final class RailgunOverloadRules {
    public static final int DYNAMIC_CAP = 96;
    public static final int CHARGING_LOAD_LV = 2;
    public static final int CHARGING_LOAD_MV = 6;
    public static final int CHARGING_LOAD_HV = 12;
    public static final int FIRE_PULSE_LV = 16;
    public static final int FIRE_PULSE_MV = 32;
    public static final int FIRE_PULSE_HV = 64;
    public static final int OVERLOAD_EXECUTION_PULSE = 96;
    public static final int OVERLOAD_EXECUTION_MAX_TICKS = 30;
    public static final int PULSE_STRIKE_LOAD_PER_RADIUS = 8;
    public static final int CHAIN_LOAD_PER_SEGMENT = 4;

    private RailgunOverloadRules() {
    }

    public static RailgunChargeTier chargingTierForCharge(long chargeUnits, int mvThreshold, int hvThreshold) {
        if (chargeUnits >= hvThreshold) {
            return RailgunChargeTier.EHV3;
        }
        if (chargeUnits >= mvThreshold) {
            return RailgunChargeTier.EHV2;
        }
        return RailgunChargeTier.EHV1;
    }

    public static int chargingLoad(RailgunChargeTier tier) {
        return switch (tier) {
            case EHV2 -> CHARGING_LOAD_MV;
            case EHV3 -> CHARGING_LOAD_HV;
            default -> CHARGING_LOAD_LV;
        };
    }

    public static int beamLoad(RailgunSettings.BeamMode mode) {
        return mode == RailgunSettings.BeamMode.EHV ? CHARGING_LOAD_MV : CHARGING_LOAD_LV;
    }

    public static int firePulse(RailgunChargeTier tier) {
        return switch (tier) {
            case EHV1 -> FIRE_PULSE_LV;
            case EHV2 -> FIRE_PULSE_MV;
            case EHV3 -> FIRE_PULSE_HV;
            default -> 0;
        };
    }

    public static int overloadExecutionPulse(boolean combo) {
        return combo ? (int) Math.round(OVERLOAD_EXECUTION_PULSE * 1.5D) : OVERLOAD_EXECUTION_PULSE;
    }

    public static int pulseStrikePulse(double radius) {
        return Math.max(0, (int) Math.ceil(radius * PULSE_STRIKE_LOAD_PER_RADIUS));
    }

    public static int chainPulse(int segments) {
        return Math.max(0, segments) * CHAIN_LOAD_PER_SEGMENT;
    }
}
