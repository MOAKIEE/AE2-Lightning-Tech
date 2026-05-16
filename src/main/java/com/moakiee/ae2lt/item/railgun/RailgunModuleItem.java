package com.moakiee.ae2lt.item.railgun;

import java.util.List;
import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

public class RailgunModuleItem extends Item implements OverloadDeviceModuleItem {
    public static final int CORE_OVERLOAD_BUDGET = 128;

    private static final Set<DeviceKind> ACCEPTS = Set.of(DeviceKind.RAILGUN);

    private final RailgunModuleType type;

    public RailgunModuleItem(Properties properties, RailgunModuleType type) {
        super(properties);
        this.type = type;
    }

    public RailgunModuleType moduleType() {
        return type;
    }

    public int getMaxInstallAmount() {
        return switch (type) {
            case CORE, ENERGY, OVERLOAD_EXECUTION -> 1;
            case COMPUTE, ACCELERATION -> 2;
        };
    }

    public int getIdleOverload() {
        return switch (type) {
            case CORE -> 0;
            case COMPUTE -> 24;
            case ACCELERATION, ENERGY -> 16;
            case OVERLOAD_EXECUTION -> 32;
        };
    }

    @Override
    public Set<DeviceKind> acceptableDevices() {
        return ACCEPTS;
    }

    @Override
    public DeviceSlotType acceptableSlot() {
        return switch (type) {
            case CORE -> DeviceSlotType.CORE;
            case COMPUTE -> DeviceSlotType.COMPUTE;
            case ACCELERATION -> DeviceSlotType.ACCELERATION;
            case ENERGY -> DeviceSlotType.ENERGY;
            case OVERLOAD_EXECUTION -> DeviceSlotType.OVERLOAD_EXECUTION;
        };
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        // Per-stack contribution. Aggregation (count of N modules) is done by services
        // iterating the resolver output — the same way the legacy *Count() helpers worked.
        return switch (type) {
            case CORE -> List.of(new DeviceCapability.OverloadTuning(CORE_OVERLOAD_BUDGET, 0.0D));
            case COMPUTE -> List.of(
                    new DeviceCapability.ChainTuning(2, 1, 0),
                    new DeviceCapability.PulseTuning(1.5D, 1.0D));
            case ACCELERATION -> List.of(new DeviceCapability.AccelerationFactor(0.30D));
            case ENERGY -> List.of(new DeviceCapability.EnergyTuning(0.50D, 0L));
            case OVERLOAD_EXECUTION -> List.of(new DeviceCapability.OverloadExecutionTuning(0.02D, 200, 8));
        };
    }
}
