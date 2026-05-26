package com.moakiee.ae2lt.item.railgun;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.registry.ModDataComponents;

public final class RailgunEnergyModuleStorage {
    private RailgunEnergyModuleStorage() {
    }

    public static ItemStack get(ItemStack railgun) {
        if (railgun == null || railgun.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return railgun
                .getOrDefault(ModDataComponents.RAILGUN_STRUCTURAL_ENERGY_MODULE.get(), ItemStack.EMPTY)
                .copyWithCount(1);
    }

    public static void set(ItemStack railgun, ItemStack energyModule) {
        if (railgun == null || railgun.isEmpty()) {
            return;
        }
        if (energyModule == null || energyModule.isEmpty()) {
            railgun.remove(ModDataComponents.RAILGUN_STRUCTURAL_ENERGY_MODULE.get());
            return;
        }
        railgun.set(ModDataComponents.RAILGUN_STRUCTURAL_ENERGY_MODULE.get(), energyModule.copyWithCount(1));
    }

    public static ItemStack remove(ItemStack railgun, int amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = get(railgun);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        set(railgun, ItemStack.EMPTY);
        return existing;
    }

    public static long capacityFe(ItemStack railgun) {
        ItemStack module = get(railgun);
        if (module.getItem() instanceof RailgunEnergyModuleItem energyModule) {
            return energyModule.capacityFe();
        }
        return 0L;
    }

    public static boolean canInstall(ItemStack candidate) {
        return candidate != null
                && !candidate.isEmpty()
                && candidate.getItem() instanceof RailgunEnergyModuleItem;
    }
}
