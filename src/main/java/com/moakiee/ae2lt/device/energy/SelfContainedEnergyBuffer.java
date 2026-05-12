package com.moakiee.ae2lt.device.energy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

/**
 * {@link DeviceEnergyBuffer} backed by an armor-internal LightningCell buffer slot.
 *
 * <p>Phase 1 ships read access only — the existing armor energy model is purely
 * passive (idle drain via tickEquipped, refill via remainingLoad²) and has no
 * "spend N now" API. Active-spend submodules (dash, flight) added in Phase 5
 * will extend this with a real {@link #tryConsume} path. Buffer capacity is
 * registry-bound and is read via the existing snapshot machinery when callers
 * need it; this interface returns 0 to flag "use snapshot for capacity".
 */
public final class SelfContainedEnergyBuffer implements DeviceEnergyBuffer {

    public static final SelfContainedEnergyBuffer INSTANCE = new SelfContainedEnergyBuffer();

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
        // Armor regenerates passively via remainingLoad² in tickEquipped.
    }
}
