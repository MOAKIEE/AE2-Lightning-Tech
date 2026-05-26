package com.moakiee.ae2lt.logic.railgun;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.item.railgun.RailgunEnergyModuleStorage;
import com.moakiee.ae2lt.item.railgun.RailgunEnergyRules;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Stateless service for the per-stack FE buffer carried by the electromagnetic
 * railgun. The buffer is manually chargeable through the standard FE item
 * capability and, when Applied Flux is present, can pull FE from the bound ME
 * network on demand when a consume call would otherwise underflow.
 */
public final class RailgunEnergyBuffer {

    private RailgunEnergyBuffer() {}

    /** Read the current buffered FE on this stack. */
    public static long read(ItemStack stack) {
        Long v = stack.get(ModDataComponents.RAILGUN_ENERGY_BUFFER.get());
        return Math.max(0L, Math.min(capacity(stack), v == null ? 0L : v));
    }

    /** Capacity = railgun base capacity plus one optional structural energy module. */
    public static long capacity(ItemStack stack) {
        return RailgunEnergyRules.capacityForExtraModuleFe(RailgunEnergyModuleStorage.capacityFe(stack));
    }

    /** Write the new buffer level, clamping to [0, capacity]. */
    public static void write(ItemStack stack, long value) {
        long clamped = Math.max(0L, Math.min(capacity(stack), value));
        stack.set(ModDataComponents.RAILGUN_ENERGY_BUFFER.get(), clamped);
    }

    /** Add {@code amount} back to the buffer after a downstream fail-soft rollback. */
    public static void refund(ItemStack stack, long amount) {
        if (amount <= 0L) return;
        write(stack, read(stack) + amount);
    }

    /**
     * Try to deduct {@code amount} FE.
     *
     * <p>Order of operations:
     * <ol>
     *   <li>If the buffer alone covers it, deduct and return true.</li>
     *   <li>Otherwise compute the shortfall and try to extract exactly that
     *       shortfall as FE from the bound ME network via AppFlux.</li>
     *   <li>On commit, drain the local buffer. No background refill occurs.</li>
     * </ol>
     *
     * @return true if the full amount was consumed; false if anything went wrong
     *         (in which case both buffer and network are unchanged)
     */
    public static boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
        if (amount <= 0L) return true;

        long buffered = read(stack);
        if (buffered >= amount) {
            write(stack, buffered - amount);
            return true;
        }

        if (!AppFluxBridge.isAvailable() || AppFluxBridge.FE_KEY == null) {
            return false;
        }
        long shortfall = amount - buffered;
        var bound = RailgunBinding.resolve(stack, player);
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
        int accepted = RailgunEnergyRules.receivableFe(read(stack), capacity(stack), amount);
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
