package com.moakiee.ae2lt.item.railgun;

import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.moakiee.ae2lt.device.overload.LockState;
import com.moakiee.ae2lt.device.overload.OverloadBudget;
import com.moakiee.ae2lt.device.overload.OverloadRuntime;

public final class RailgunOverloadBudget implements OverloadBudget {
    public static final RailgunOverloadBudget INSTANCE = new RailgunOverloadBudget();
    public static final String STATE_CHARGING = "charging";
    public static final String STATE_BEAM = "beam";

    private static final String TAG_ROOT = "RailgunOverload";
    private static final String TAG_DEVICE_ID = "DeviceId";

    private RailgunOverloadBudget() {}

    @Override
    public int currentLoad(ItemStack stack) {
        UUID id = getDeviceId(stack);
        return id == null ? 0 : OverloadRuntime.get(id).bucket().current();
    }

    @Override
    public int budgetCap(ItemStack stack) {
        return RailgunStructuralCore.baseOverloadBudget(stack);
    }

    @Override
    public LockState lockState(ItemStack stack) {
        UUID id = getDeviceId(stack);
        return id == null ? LockState.UNLOCKED : OverloadRuntime.get(id).dynamics().state();
    }

    @Override
    public void tick(ItemStack stack, Player player) {
        UUID id = getDeviceId(stack);
        if (id == null) {
            return;
        }
        OverloadRuntime.get(id).tick(budgetCap(stack));
    }

    @Override
    public void contributeState(ItemStack stack, String key, int loadPerTick) {
        OverloadRuntime.get(ensureDeviceId(stack)).bucket().setState(key, loadPerTick);
    }

    @Override
    public void clearState(ItemStack stack, String key) {
        UUID id = getDeviceId(stack);
        if (id != null) {
            OverloadRuntime.get(id).bucket().clearState(key);
        }
    }

    @Override
    public void contributePulse(ItemStack stack, int base) {
        OverloadRuntime.get(ensureDeviceId(stack)).bucket().addPulse(base);
    }

    @Override
    public void contributePulse(ItemStack stack, int base, double decay, int maxTicks) {
        OverloadRuntime.get(ensureDeviceId(stack)).bucket().addPulse(base, decay, maxTicks);
    }

    public void setChargingLoad(ItemStack stack, RailgunChargeTier tier) {
        contributeState(stack, STATE_CHARGING, RailgunOverloadRules.chargingLoad(tier));
    }

    public void clearCharging(ItemStack stack) {
        clearState(stack, STATE_CHARGING);
    }

    public void setBeamLoad(ItemStack stack, RailgunSettings.BeamMode mode) {
        contributeState(stack, STATE_BEAM, RailgunOverloadRules.beamLoad(mode));
    }

    public void clearBeam(ItemStack stack) {
        clearState(stack, STATE_BEAM);
    }

    public void addFirePulse(ItemStack stack, RailgunChargeTier tier) {
        contributePulse(stack, RailgunOverloadRules.firePulse(tier));
    }

    public void addPulseStrike(ItemStack stack, double radius) {
        contributePulse(stack, RailgunOverloadRules.pulseStrikePulse(radius));
    }

    public void addChainPulse(ItemStack stack, int segments) {
        contributePulse(stack, RailgunOverloadRules.chainPulse(segments));
    }

    public void addOverloadExecutionPulse(ItemStack stack) {
        addOverloadExecutionPulse(stack, false);
    }

    public void addOverloadExecutionPulse(ItemStack stack, boolean combo) {
        contributePulse(
                stack,
                RailgunOverloadRules.overloadExecutionPulse(combo),
                1.0D,
                RailgunOverloadRules.OVERLOAD_EXECUTION_MAX_TICKS);
    }

    public boolean isLocked(ItemStack stack) {
        return lockState(stack) instanceof LockState.Locked;
    }

    public static UUID ensureDeviceId(ItemStack stack) {
        UUID existing = getDeviceId(stack);
        if (existing != null) {
            return existing;
        }
        UUID created = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, rootTag -> {
            CompoundTag overload = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();
            overload.putUUID(TAG_DEVICE_ID, created);
            rootTag.put(TAG_ROOT, overload);
        });
        return created;
    }

    public static UUID getDeviceId(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag overload = root.getCompound(TAG_ROOT);
        return overload.hasUUID(TAG_DEVICE_ID) ? overload.getUUID(TAG_DEVICE_ID) : null;
    }
}
