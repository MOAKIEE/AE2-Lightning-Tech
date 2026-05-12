package com.moakiee.ae2lt.device.energy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;

/**
 * {@link DeviceEnergyBuffer} backed by an AE network bound to the holding device
 * (the railgun). Thin wrapper over the existing static
 * {@link RailgunEnergyBuffer} service so existing code paths are untouched.
 */
public final class NetworkBoundEnergyBuffer implements DeviceEnergyBuffer {

    public static final NetworkBoundEnergyBuffer INSTANCE = new NetworkBoundEnergyBuffer();

    private NetworkBoundEnergyBuffer() {}

    @Override
    public long stored(ItemStack stack) {
        return RailgunEnergyBuffer.read(stack);
    }

    @Override
    public long capacity(ItemStack stack) {
        return RailgunEnergyBuffer.capacity();
    }

    @Override
    public boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
        return RailgunEnergyBuffer.tryConsume(stack, player, amount);
    }

    @Override
    public void refill(ItemStack stack, ServerPlayer player) {
        RailgunEnergyBuffer.refillFromNetwork(stack, player);
    }
}
