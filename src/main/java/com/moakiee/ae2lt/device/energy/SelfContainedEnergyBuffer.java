package com.moakiee.ae2lt.device.energy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;

import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

/**
 * {@link DeviceEnergyBuffer} backed by an armor-internal LightningCell buffer slot.
 *
 * <p>The existing armor runtime still owns passive generation and debt/lock
 * handling. This adapter only tops up the local buffer from the bound AE network.
 */
public final class SelfContainedEnergyBuffer implements DeviceEnergyBuffer {

    public static final SelfContainedEnergyBuffer INSTANCE = new SelfContainedEnergyBuffer();
    private static final int NETWORK_REFILL_INTERVAL_TICKS = 20;

    private SelfContainedEnergyBuffer() {}

    @Override
    public long stored(ItemStack stack) {
        return OverloadArmorState.readPersistedStoredEnergy(stack);
    }

    @Override
    public long capacity(ItemStack stack) {
        return 0L;
    }

    @Override
    public boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
        return false;
    }

    @Override
    public void refill(ItemStack stack, ServerPlayer player) {
        if (player.level().getGameTime() % NETWORK_REFILL_INTERVAL_TICKS != 0L) {
            return;
        }

        var registries = player.level().registryAccess();
        var snapshot = OverloadArmorState.snapshot(player, stack, registries, true);
        if (!snapshot.hasCore() || !snapshot.hasBuffer() || snapshot.bufferCapacity() <= 0L) {
            return;
        }

        long current = Math.min(snapshot.storedEnergy(), snapshot.bufferCapacity());
        long wanted = snapshot.bufferCapacity() - current;
        if (wanted <= 0L) {
            return;
        }

        var resolved = ArmorNetworkBinding.INSTANCE.resolve(stack, player);
        if (!resolved.success() || resolved.grid() == null) {
            return;
        }

        IEnergyService energy = resolved.grid().getEnergyService();
        double available = energy.extractAEPower(wanted, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (available <= 0.5D) {
            return;
        }

        long toExtract = Math.min(wanted, (long) Math.floor(available));
        if (toExtract <= 0L) {
            return;
        }

        double extracted = energy.extractAEPower(toExtract, Actionable.MODULATE, PowerMultiplier.CONFIG);
        OverloadArmorState.addStoredEnergy(stack, registries, Math.min(wanted, (long) Math.floor(extracted)));
    }
}
