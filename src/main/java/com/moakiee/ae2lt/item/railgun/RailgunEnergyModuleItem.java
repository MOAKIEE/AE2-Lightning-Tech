package com.moakiee.ae2lt.item.railgun;

import java.util.List;
import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

public final class RailgunEnergyModuleItem extends Item implements OverloadDeviceModuleItem {
    private static final Set<DeviceKind> ACCEPTS = Set.of(DeviceKind.RAILGUN);

    private final long capacityFe;

    public RailgunEnergyModuleItem(Properties properties, long capacityFe) {
        super(properties);
        this.capacityFe = Math.max(0L, capacityFe);
    }

    public long capacityFe() {
        return capacityFe;
    }

    @Override
    public Set<DeviceKind> acceptableDevices() {
        return ACCEPTS;
    }

    @Override
    public DeviceSlotType acceptableSlot() {
        return DeviceSlotType.ENERGY;
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return List.of(new DeviceCapability.EnergyCapacity(capacityFe));
    }
}
