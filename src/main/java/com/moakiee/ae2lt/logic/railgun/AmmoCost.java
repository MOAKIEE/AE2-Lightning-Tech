package com.moakiee.ae2lt.logic.railgun;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunModules;

/**
 * Per-shot resource cost for a charged railgun fire. The {@code energy} module
 * trims AE & EHV costs by 25% (rounded up).
 */
public record AmmoCost(long aeEnergy, long ehv) {

    public static AmmoCost forCharged(RailgunChargeTier tier, RailgunModules mods) {
        long ae;
        long ehv;
        switch (tier) {
            case EHV1 -> { ae = AE2LTCommonConfig.railgunAeCostTier1(); ehv = AE2LTCommonConfig.railgunEhvCostTier1(); }
            case EHV2 -> { ae = AE2LTCommonConfig.railgunAeCostTier2(); ehv = AE2LTCommonConfig.railgunEhvCostTier2(); }
            case EHV3 -> { ae = AE2LTCommonConfig.railgunAeCostTier3(); ehv = AE2LTCommonConfig.railgunEhvCostTier3(); }
            default -> { ae = 0L; ehv = 0L; }
        }
        if (mods.hasEnergy()) {
            ae = (long) Math.ceil(ae * 0.75D);
            ehv = (long) Math.ceil(ehv * 0.75D);
        }
        return new AmmoCost(ae, ehv);
    }

    public static long beamAeCost(RailgunModules mods) {
        long ae = AE2LTCommonConfig.railgunBeamAeCostPerSettle();
        if (mods.hasEnergy()) {
            ae = (long) Math.ceil(ae * 0.5D);
        }
        return ae;
    }

    public static int beamHvCostInterval(RailgunModules mods) {
        int n = AE2LTCommonConfig.railgunBeamHvCostInterval();
        if (mods.hasEnergy()) {
            n *= 2;
        }
        return Math.max(1, n);
    }
}
