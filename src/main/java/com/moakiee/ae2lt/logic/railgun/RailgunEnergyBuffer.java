package com.moakiee.ae2lt.logic.railgun;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Stateless service for the per-stack AE energy buffer carried by the
 * electromagnetic railgun. The buffer smooths large single-shot AE spikes
 * (a tier-3 charged shot is 200,000 AE) so small/mid-tier networks can sustain
 * bursts they otherwise could not, and gives the player a visible energy
 * reserve via tooltip / HUD.
 *
 * <p>Design contract:
 * <ul>
 *   <li><b>Buffer covers AE only.</b> HV/EHV ammunition is still extracted
 *       directly from the bound ME network's storage service in
 *       {@link RailgunFireService}/{@link RailgunBeamService}. The buffer does
 *       not enable offline play — without ammo the gun still cannot fire.</li>
 *   <li><b>Fail-soft consumption.</b> {@link #tryConsume} prefers to deduct
 *       from the buffer; if short, it pulls the remainder from the network in
 *       a {@code SIMULATE → MODULATE} pair so any failure leaves both buffer
 *       and network untouched. Mirrors the EHV/HV compensation pattern in
 *       {@link RailgunFireService#fireCharged}.</li>
 *   <li><b>Refill is held-only and silent.</b> {@link #refillFromNetwork} is
 *       called from {@code Item.inventoryTick} when the stack is in main hand
 *       or off-hand; if the network is unreachable for any reason the call
 *       returns silently (no chat message, no log spam) — the player may have
 *       wandered out of range or switched dimensions.</li>
 *   <li><b>No FE capability.</b> The railgun deliberately does not register
 *       {@code Capabilities.EnergyStorage.ITEM}; FE players feed the network
 *       through AppFlux flux cells (see {@code AppFluxBridge}), preserving
 *       the ME-bound identity of the weapon.</li>
 * </ul>
 */
public final class RailgunEnergyBuffer {

    private RailgunEnergyBuffer() {}

    /** Read the current buffered AE on this stack (0 if absent). */
    public static long read(ItemStack stack) {
        Long v = stack.get(ModDataComponents.RAILGUN_AE_BUFFER.get());
        return v == null ? 0L : Math.max(0L, v);
    }

    /** Configured capacity (clamped to current config). */
    public static long capacity() {
        return Math.max(0L, AE2LTCommonConfig.railgunBufferCapacity());
    }

    /** Write the new buffer level, clamping to [0, capacity]. */
    public static void write(ItemStack stack, long value) {
        long clamped = Math.max(0L, Math.min(capacity(), value));
        stack.set(ModDataComponents.RAILGUN_AE_BUFFER.get(), clamped);
    }

    /** Add {@code amount} back to the buffer (used to roll back AE when EHV/HV
     *  extraction fails downstream). Saturates at capacity — overflow is
     *  silently dropped, matching the behaviour of network re-insertion in
     *  the existing fail-soft paths. */
    public static void refund(ItemStack stack, long amount) {
        if (amount <= 0L) return;
        write(stack, read(stack) + amount);
    }

    /**
     * Try to deduct {@code amount} AE.
     *
     * <p>Order of operations:
     * <ol>
     *   <li>If the buffer alone covers it, deduct and return true.</li>
     *   <li>Otherwise compute the shortfall, resolve the bound grid, and try
     *       to extract the shortfall from the network's energy service. If
     *       that simulation fails, return false without modifying anything.</li>
     *   <li>On commit, drain the buffer and modulate-extract the shortfall.</li>
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

        long shortfall = amount - buffered;
        var bound = RailgunBinding.resolve(stack, player);
        if (!bound.success()) {
            return false;
        }
        IGrid grid = bound.grid();
        if (grid == null) {
            return false;
        }
        IEnergyService energy = grid.getEnergyService();
        double sim = energy.extractAEPower(shortfall, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (sim < shortfall) {
            return false;
        }
        energy.extractAEPower(shortfall, Actionable.MODULATE, PowerMultiplier.CONFIG);
        write(stack, 0L);
        return true;
    }

    /**
     * Best-effort top-up from the bound network. Called from
     * {@link com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem#inventoryTick}
     * while the stack is held. Internally throttled by
     * {@link AE2LTCommonConfig#railgunBufferRefillIntervalTicks()} so the
     * grid energy service is hit at most every N ticks per held railgun.
     *
     * <p>Per-call top-up amount = {@code refillRatePerTick * refillInterval},
     * clamped by both the remaining buffer space and what the network actually
     * has available. Silent on every failure mode (unbound, out of range,
     * dimension not loaded, no AE available).
     */
    public static void refillFromNetwork(ItemStack stack, ServerPlayer player) {
        long ratePerTick = AE2LTCommonConfig.railgunBufferRefillRatePerTick();
        if (ratePerTick <= 0L) return;
        int interval = Math.max(1, AE2LTCommonConfig.railgunBufferRefillIntervalTicks());
        if (player.level().getGameTime() % interval != 0L) return;

        long current = read(stack);
        long cap = capacity();
        if (current >= cap) return;
        long want = Math.min(cap - current, ratePerTick * interval);
        if (want <= 0L) return;

        var bound = RailgunBinding.resolve(stack, player);
        if (!bound.success()) return;
        IGrid grid = bound.grid();
        if (grid == null) return;

        IEnergyService energy = grid.getEnergyService();
        double sim = energy.extractAEPower(want, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (sim <= 0.5D) return;
        long take = Math.min(want, (long) Math.floor(sim));
        if (take <= 0L) return;

        double got = energy.extractAEPower(take, Actionable.MODULATE, PowerMultiplier.CONFIG);
        long add = (long) Math.floor(got);
        if (add <= 0L) return;
        write(stack, current + add);
    }
}
