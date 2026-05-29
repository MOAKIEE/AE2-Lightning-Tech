package com.moakiee.ae2lt.overload.armor.service;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.overload.armor.ArmorEnergyBuffer;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.overload.armor.service.ArmorLightningService.LightningCost;

public final class ArmorEnergyService {
    private ArmorEnergyService() {
    }

    public static long refillFromBoundNetwork(Player player, ItemStack armor, HolderLookup.Provider registries) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0L;
        }
        return ArmorEnergyBuffer.refillFromNetwork(armor, serverPlayer, Long.MAX_VALUE);
    }

    public static boolean consumePassiveDrain(Player player, ItemStack armor, HolderLookup.Provider registries) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        var cost = computePassiveCost(serverPlayer, armor, registries);
        if (!ArmorLightningService.hasCost(serverPlayer, armor, cost.lightning())) {
            return false;
        }
        if (!consumeCost(serverPlayer, armor, cost.fe(), "energy")) {
            return false;
        }
        if (ArmorLightningService.consume(serverPlayer, armor, cost.lightning())) {
            return true;
        }
        refundCost(serverPlayer, armor, cost.fe());
        return false;
    }

    public static boolean consumePassiveDrain(Player player, ItemStack armor, long amount, String reason) {
        return consumeCost(player, armor, amount, reason);
    }

    public static boolean consumeActiveCost(Player player, ItemStack armor, long amount, String reason) {
        return consumeCost(player, armor, amount, reason);
    }

    private static boolean consumeCost(Player player, ItemStack armor, long amount, String reason) {
        if (amount <= 0L) {
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ArmorEnergyBuffer.refillFromNetwork(
                armor,
                serverPlayer,
                Math.max(0L, amount - ArmorEnergyBuffer.read(armor, serverPlayer.registryAccess())));
        boolean paid = ArmorEnergyBuffer.tryConsume(armor, serverPlayer, amount);
        return paid;
    }

    private static void refundCost(ServerPlayer player, ItemStack armor, long amount) {
        if (amount <= 0L) {
            return;
        }
        ArmorEnergyBuffer.write(
                armor,
                player.registryAccess(),
                ArmorEnergyBuffer.read(armor, player.registryAccess()) + amount);
    }

    private static PassiveCost computePassiveCost(ServerPlayer player, ItemStack armor, HolderLookup.Provider registries) {
        long drain = 0L;
        double multiplier = 1.0D;
        LightningCost lightning = LightningCost.NONE;
        for (ItemStack module : OverloadArmorState.loadModuleStacks(armor, registries)) {
            if (!(module.getItem() instanceof OverloadDeviceModuleItem provider)) {
                continue;
            }
            if (!moduleRuntimeActive(armor, module)) {
                continue;
            }
            List<DeviceCapability> capabilities = provider.capabilities(module);
            boolean movingFlight = hasFlightMode(capabilities) && isMovingInFlight(player);
            LightningCost moduleLightning = passiveLightningCost(capabilities, movingFlight)
                    .times(Math.max(1, module.getCount()));
            lightning = lightning.plus(moduleLightning);
            for (DeviceCapability capability : capabilities) {
                if (capability instanceof DeviceCapability.PassiveDrain passiveDrain) {
                    long fePerTick = Math.max(0L, passiveDrain.fePerTick());
                    if (movingFlight) {
                        fePerTick = Math.max(fePerTick, ArmorOverloadRules.FLIGHT_MOVING_DRAIN_FE);
                    }
                    drain += fePerTick * Math.max(1, module.getCount());
                } else if (capability instanceof DeviceCapability.EnergyEfficiency efficiency) {
                    multiplier *= Math.max(0.0D, efficiency.drainMul());
                }
            }
        }
        return new PassiveCost((long) Math.ceil(drain * multiplier), lightning);
    }

    private static LightningCost passiveLightningCost(List<DeviceCapability> capabilities, boolean movingFlight) {
        LightningCost cost = LightningCost.NONE;
        boolean flight = false;
        boolean phaseFlight = false;
        for (DeviceCapability capability : capabilities) {
            if (capability instanceof DeviceCapability.FlightMode mode) {
                flight = true;
                phaseFlight = mode.kind() == com.moakiee.ae2lt.device.capability.FlightKind.PHASE;
            }
        }
        if (phaseFlight) {
            cost = cost.plus(LightningCost.ehv(AE2LTCommonConfig.overloadArmorPhaseFlightEhvPerTick()));
        } else if (flight) {
            cost = cost.plus(LightningCost.hv(AE2LTCommonConfig.overloadArmorFlightHvPerTick()));
            if (movingFlight) {
                cost = cost.plus(LightningCost.hv(AE2LTCommonConfig.overloadArmorFlightHvPerTick()));
            }
        } else {
            cost = cost.plus(LightningCost.hv(AE2LTCommonConfig.overloadArmorPassiveHvPerTick()));
        }
        return cost;
    }

    private static boolean moduleRuntimeActive(ItemStack armor, ItemStack module) {
        if (!(module.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
            return false;
        }
        boolean[] active = {false};
        provider.collectSubmodules(module, submodule -> {
            if (submodule != null && OverloadArmorState.isSubmoduleRuntimeActive(armor, submodule.id())) {
                active[0] = true;
            }
        });
        return active[0];
    }

    private static boolean hasFlightMode(List<DeviceCapability> capabilities) {
        for (DeviceCapability capability : capabilities) {
            if (capability instanceof DeviceCapability.FlightMode) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMovingInFlight(ServerPlayer player) {
        if (!player.getAbilities().flying) {
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        return motion.lengthSqr() > 1.0E-4D;
    }

    private record PassiveCost(long fe, LightningCost lightning) {
    }
}
