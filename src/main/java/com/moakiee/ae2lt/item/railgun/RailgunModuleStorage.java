package com.moakiee.ae2lt.item.railgun;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.DeviceModuleStorage;
import com.moakiee.ae2lt.registry.ModDataComponents;

public final class RailgunModuleStorage implements DeviceModuleStorage {
    public static final RailgunModuleStorage INSTANCE = new RailgunModuleStorage();

    private RailgunModuleStorage() {}

    @Override
    public DeviceKind deviceKind() {
        return DeviceKind.RAILGUN;
    }

    @Override
    public int baseOverloadBudget(ItemStack device) {
        return baseOverloadBudget(entryData(device));
    }

    @Override
    public int currentIdleOverload(ItemStack device) {
        return currentIdleOverload(entryData(device));
    }

    @Override
    public List<ItemStack> listEntries(ItemStack device) {
        return entryData(device).entries().stream()
                .map(ItemStack::copy)
                .toList();
    }

    @Override
    public int getCount(ItemStack device, String typeId) {
        return entryData(device).getCount(typeId);
    }

    @Override
    public boolean canInstallOne(ItemStack device, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (!(candidate.getItem() instanceof RailgunModuleItem module)) {
            return false;
        }

        var entries = entryData(device);
        if (entries.getCount(module.moduleType()) >= module.getMaxInstallAmount()) {
            return false;
        }

        int nextLoad = currentIdleOverload(entries) + module.getIdleOverload();
        return nextLoad <= baseOverloadBudgetAfterInstall(entries, candidate);
    }

    @Override
    public boolean installOne(ItemStack device, ItemStack candidate) {
        if (!canInstallOne(device, candidate)) {
            return false;
        }

        var stacks = new ArrayList<>(listEntries(device));
        String typeId = RailgunModuleEntries.typeId(candidate);
        for (var stack : stacks) {
            if (typeId.equals(RailgunModuleEntries.typeId(stack))) {
                stack.grow(1);
                setEntries(device, new RailgunModuleEntries(stacks));
                return true;
            }
        }
        stacks.add(candidate.copyWithCount(1));
        setEntries(device, new RailgunModuleEntries(stacks));
        return true;
    }

    @Override
    public ItemStack uninstallOne(ItemStack device, String typeId) {
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
            setEntries(device, new RailgunModuleEntries(stacks));
            return detached;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack uninstallAll(ItemStack device, String typeId) {
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
            setEntries(device, new RailgunModuleEntries(stacks));
            return detached;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasAnyInstalled(ItemStack device) {
        return !entryData(device).entries().isEmpty();
    }

    @Override
    public Stream<ItemStack> installedModuleStacks(ItemStack device) {
        return entryData(device).installedModuleStacks();
    }

    public List<DeviceCapability> capabilities(ItemStack device) {
        return entryData(device).capabilities();
    }

    public static RailgunModuleEntries entryData(ItemStack device) {
        return device.getOrDefault(
                ModDataComponents.RAILGUN_MODULE_ENTRIES.get(),
                RailgunModuleEntries.EMPTY);
    }

    public static void setEntries(ItemStack device, RailgunModuleEntries entries) {
        if (entries == null || entries.entries().isEmpty()) {
            device.remove(ModDataComponents.RAILGUN_MODULE_ENTRIES.get());
        } else {
            device.set(ModDataComponents.RAILGUN_MODULE_ENTRIES.get(), entries);
        }
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
