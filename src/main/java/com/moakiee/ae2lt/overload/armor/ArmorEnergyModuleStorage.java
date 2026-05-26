package com.moakiee.ae2lt.overload.armor;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.registry.ModDataComponents;

public final class ArmorEnergyModuleStorage {
    private ArmorEnergyModuleStorage() {
    }

    public static ItemStack get(ItemStack armor) {
        if (armor == null || armor.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return armor.getOrDefault(ModDataComponents.ARMOR_STRUCTURAL_ENERGY_MODULE.get(), ItemStack.EMPTY)
                .copyWithCount(1);
    }

    public static void set(ItemStack armor, ItemStack energyModule) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
        if (energyModule == null || energyModule.isEmpty()) {
            armor.remove(ModDataComponents.ARMOR_STRUCTURAL_ENERGY_MODULE.get());
        } else {
            armor.set(ModDataComponents.ARMOR_STRUCTURAL_ENERGY_MODULE.get(), energyModule.copyWithCount(1));
        }
        ArmorEnergyBuffer.clamp(armor);
    }

    public static long capacityFe(ItemStack armor) {
        ItemStack module = get(armor);
        if (module.getItem() instanceof ArmorEnergyModuleItem energyModule) {
            return energyModule.capacityFe();
        }
        return 0L;
    }

    public static boolean canInstall(ItemStack candidate) {
        return candidate != null
                && !candidate.isEmpty()
                && candidate.getItem() instanceof ArmorEnergyModuleItem;
    }
}
