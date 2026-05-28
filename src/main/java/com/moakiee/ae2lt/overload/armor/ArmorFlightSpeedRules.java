package com.moakiee.ae2lt.overload.armor;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.armor.module.FlightSpeedOption;
import com.moakiee.ae2lt.overload.armor.module.FlightSubmodule;
import com.moakiee.ae2lt.overload.armor.module.PhaseFlightSubmodule;

public final class ArmorFlightSpeedRules {
    private ArmorFlightSpeedRules() {
    }

    public static float activeFlightSpeed(ItemStack armor) {
        if (OverloadArmorState.isSubmoduleRuntimeActive(armor, PhaseFlightSubmodule.INSTANCE.id())) {
            return (float) Math.max(FlightSpeedOption.VANILLA_FLYING_SPEED, PhaseFlightSubmodule.phaseSpeed(armor));
        }
        if (OverloadArmorState.isSubmoduleRuntimeActive(armor, FlightSubmodule.INSTANCE.id())) {
            return FlightSubmodule.flightSpeed(armor);
        }
        return FlightSpeedOption.VANILLA_FLYING_SPEED;
    }
}
