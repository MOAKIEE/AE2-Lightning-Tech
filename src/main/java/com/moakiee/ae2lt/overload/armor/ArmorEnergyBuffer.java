package com.moakiee.ae2lt.overload.armor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.registry.ModDataComponents;

public final class ArmorEnergyBuffer {
    private ArmorEnergyBuffer() {
    }

    public static long read(ItemStack stack) {
        Long value = stack.get(ModDataComponents.ARMOR_ENERGY_BUFFER.get());
        return Math.max(0L, Math.min(capacity(stack), value == null ? 0L : value));
    }

    public static long capacity(ItemStack stack) {
        return ArmorEnergyRules.capacityForExtraModuleFe(ArmorEnergyModuleStorage.capacityFe(stack));
    }

    public static void write(ItemStack stack, long value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(ModDataComponents.ARMOR_ENERGY_BUFFER.get(), Math.max(0L, Math.min(capacity(stack), value)));
    }

    public static void clamp(ItemStack stack) {
        write(stack, read(stack));
    }

    public static boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        long buffered = read(stack);
        if (buffered >= amount) {
            write(stack, buffered - amount);
            return true;
        }
        if (!AppFluxBridge.isAvailable() || AppFluxBridge.FE_KEY == null) {
            return false;
        }

        long shortfall = amount - buffered;
        var bound = ArmorNetworkBinding.INSTANCE.resolve(stack, player);
        if (!bound.success()) {
            return false;
        }
        IGrid grid = bound.grid();
        if (grid == null) {
            return false;
        }
        var storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofPlayer(player);
        long sim = storage.extract(AppFluxBridge.FE_KEY, shortfall, Actionable.SIMULATE, source);
        if (sim < shortfall) {
            return false;
        }
        long got = storage.extract(AppFluxBridge.FE_KEY, shortfall, Actionable.MODULATE, source);
        if (got < shortfall) {
            if (got > 0L) {
                storage.insert(AppFluxBridge.FE_KEY, got, Actionable.MODULATE, source);
            }
            return false;
        }
        write(stack, 0L);
        return true;
    }

    public static int receiveFe(ItemStack stack, int amount, boolean simulate) {
        int accepted = ArmorEnergyRules.receivableFe(read(stack), capacity(stack), amount);
        if (!simulate && accepted > 0) {
            write(stack, read(stack) + accepted);
        }
        return accepted;
    }

    public static IEnergyStorage asEnergyStorage(ItemStack stack) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return receiveFe(stack, maxReceive, simulate);
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, read(stack));
            }

            @Override
            public int getMaxEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, capacity(stack));
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }
}
