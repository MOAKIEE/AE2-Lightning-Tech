package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.ReachSubmodule;

public final class ReachSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public ReachSubmoduleItem(Properties properties) {
        super(
                properties.stacksTo(1),
                ArmorPart.CHEST,
                ReachSubmodule.INSTANCE,
                "item.ae2lt.module_reach_extension.tooltip",
                stack -> List.of(
                        new DeviceCapability.InteractionRange(),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.REACH_EXTENSION_PASSIVE_DRAIN_FE)));
    }
}
