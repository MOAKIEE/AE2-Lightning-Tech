package com.moakiee.ae2lt.item.railgun;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.registry.ModDataComponents;

public final class RailgunModuleStorage {
    private RailgunModuleStorage() {}

    public static RailgunModuleEntries get(ItemStack device) {
        return device.getOrDefault(
                ModDataComponents.RAILGUN_MODULE_ENTRIES.get(),
                RailgunModuleEntries.EMPTY);
    }

    public static void set(ItemStack device, RailgunModuleEntries entries) {
        if (entries == null || entries.entries().isEmpty()) {
            device.remove(ModDataComponents.RAILGUN_MODULE_ENTRIES.get());
        } else {
            device.set(ModDataComponents.RAILGUN_MODULE_ENTRIES.get(), entries);
        }
    }

    public static List<ItemStack> listEntries(ItemStack device) {
        return get(device).entries().stream()
                .map(ItemStack::copy)
                .toList();
    }

    public static int getCount(ItemStack device, String typeId) {
        return get(device).getCount(typeId);
    }

    public static boolean canInstallOne(ItemStack device, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (!(candidate.getItem() instanceof RailgunModuleItem module)) {
            return false;
        }

        var entries = get(device);
        if (entries.getCount(module.moduleType()) >= module.getMaxInstallAmount()) {
            return false;
        }

        int nextLoad = currentIdleOverload(entries) + module.getIdleOverload();
        return nextLoad <= baseOverloadBudgetAfterInstall(entries, candidate);
    }

    public static boolean installOne(ItemStack device, ItemStack candidate) {
        if (!canInstallOne(device, candidate)) {
            return false;
        }

        var stacks = new ArrayList<>(listEntries(device));
        String typeId = RailgunModuleEntries.typeId(candidate);
        for (var stack : stacks) {
            if (typeId.equals(RailgunModuleEntries.typeId(stack))) {
                stack.grow(1);
                set(device, new RailgunModuleEntries(stacks));
                return true;
            }
        }
        stacks.add(candidate.copyWithCount(1));
        set(device, new RailgunModuleEntries(stacks));
        return true;
    }

    public static ItemStack uninstallOne(ItemStack device, String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return ItemStack.EMPTY;
        }

        var stacks = new ArrayList<>(listEntries(device));
        for (int index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (!typeId.equals(RailgunModuleEntries.typeId(stack))) {
                continue;
            }
            var detached = stack.copyWithCount(1);
            if (stack.getCount() <= 1) {
                stacks.remove(index);
            } else {
                stack.shrink(1);
            }
            set(device, new RailgunModuleEntries(stacks));
            return detached;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack uninstallAll(ItemStack device, String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return ItemStack.EMPTY;
        }

        var stacks = new ArrayList<>(listEntries(device));
        for (int index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (!typeId.equals(RailgunModuleEntries.typeId(stack))) {
                continue;
            }
            var detached = stack.copy();
            stacks.remove(index);
            set(device, new RailgunModuleEntries(stacks));
            return detached;
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasAnyInstalled(ItemStack device) {
        return !get(device).entries().isEmpty();
    }

    public static Stream<ItemStack> installedModuleStacks(ItemStack device) {
        return get(device).installedModuleStacks();
    }

    public static List<DeviceCapability> capabilities(ItemStack device) {
        return get(device).capabilities();
    }

    public static int baseOverloadBudget(ItemStack device) {
        return baseOverloadBudget(get(device));
    }

    public static int currentIdleOverload(ItemStack device) {
        return currentIdleOverload(get(device));
    }

    private static int baseOverloadBudget(RailgunModuleEntries entries) {
        ItemStack core = entries.first(RailgunModuleType.CORE);
        return baseOverloadBudgetFromCore(core);
    }

    private static int currentIdleOverload(RailgunModuleEntries entries) {
        int total = 0;
        for (var stack : entries.entries()) {
            if (stack.getItem() instanceof RailgunModuleItem module) {
                total += module.getIdleOverload() * stack.getCount();
            }
        }
        return total;
    }

    private static int baseOverloadBudgetAfterInstall(RailgunModuleEntries entries, ItemStack candidate) {
        if (candidate.getItem() instanceof RailgunModuleItem module
                && module.moduleType() == RailgunModuleType.CORE) {
            return baseOverloadBudgetFromCore(candidate);
        }
        return baseOverloadBudget(entries);
    }

    private static int baseOverloadBudgetFromCore(ItemStack core) {
        if (core.isEmpty() || !(core.getItem() instanceof RailgunModuleItem module)) {
            return 0;
        }
        for (var capability : module.capabilities(core)) {
            if (capability instanceof DeviceCapability.OverloadTuning tuning) {
                return Math.max(tuning.budgetCap(), 0);
            }
        }
        return 0;
    }
}
