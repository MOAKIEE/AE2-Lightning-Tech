package com.moakiee.ae2lt.device.module;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public final class ArmorModuleStorage implements DeviceModuleStorage {
    public static final ArmorModuleStorage INSTANCE = new ArmorModuleStorage();

    private ArmorModuleStorage() {}

    @Override
    public DeviceKind deviceKind() {
        return DeviceKind.OVERLOAD_ARMOR;
    }

    @Override
    public int baseOverloadBudget(ItemStack device) {
        return OverloadArmorState.getBaseOverload(device, null);
    }

    @Override
    public int currentIdleOverload(ItemStack device) {
        return OverloadArmorState.computeTotalIdleOverload(device, null);
    }

    @Override
    public List<ItemStack> listEntries(ItemStack device) {
        return OverloadArmorState.loadModuleStacks(device, null).stream()
                .map(ItemStack::copy)
                .toList();
    }

    @Override
    public int getCount(ItemStack device, String typeId) {
        return OverloadArmorState.getInstalledAmount(device, null, typeId);
    }

    @Override
    public boolean canInstallOne(ItemStack device, ItemStack candidate) {
        return OverloadArmorState.canInstallModule(device, null, candidate);
    }

    @Override
    public boolean installOne(ItemStack device, ItemStack candidate) {
        return OverloadArmorState.installOneModule(device, null, candidate);
    }

    @Override
    public ItemStack uninstallOne(ItemStack device, String typeId) {
        return OverloadArmorState.uninstallOneModule(device, null, typeId);
    }

    @Override
    public ItemStack uninstallAll(ItemStack device, String typeId) {
        return OverloadArmorState.uninstallAllOfType(device, null, typeId);
    }

    @Override
    public boolean hasAnyInstalled(ItemStack device) {
        return OverloadArmorState.hasAnyInstalledModule(device, null);
    }

    @Override
    public Stream<ItemStack> installedModuleStacks(ItemStack device) {
        var result = new ArrayList<ItemStack>();
        for (var stack : listEntries(device)) {
            for (int i = 0; i < stack.getCount(); i++) {
                result.add(stack.copyWithCount(1));
            }
        }
        return result.stream();
    }
}
