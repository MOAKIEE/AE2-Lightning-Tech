package com.moakiee.ae2lt.logic.railgun;

import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunEnergyRules;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;

/**
 * Per-shot FE and lightning-ammo costs for railgun actions.
 */
public record AmmoCost(long aeEnergy, long ehv) {

    public static AmmoCost forCharged(RailgunChargeTier tier, RailgunModuleEntries mods) {
        long ehv = switch (tier) {
            case EHV1 -> 1L;
            case EHV2 -> 2L;
            case EHV3 -> 4L;
            default -> 0L;
        };
        return new AmmoCost(RailgunEnergyRules.fireCostFe(tier), ehv);
    }

    public static long beamAeCost(RailgunModuleEntries mods) {
        return RailgunEnergyRules.CHARGE_COST_LV_PER_TICK_FE;
    }

    public static int beamHvCostInterval(RailgunModuleEntries mods) {
        return 1;
    }
}
