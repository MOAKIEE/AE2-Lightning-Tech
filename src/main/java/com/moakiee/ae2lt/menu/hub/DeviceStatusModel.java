package com.moakiee.ae2lt.menu.hub;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.overload.LockState;
import com.moakiee.ae2lt.device.overload.OverloadRuntime;
import com.moakiee.ae2lt.item.railgun.RailgunModuleStorage;
import com.moakiee.ae2lt.item.railgun.RailgunOverloadBudget;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.logic.railgun.RailgunBinding;
import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;
import com.moakiee.ae2lt.overload.armor.ArmorEnergyBuffer;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.BaseOverloadArmorItem;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Immutable snapshot of a device's current status, built server-side and synced to the client.
 */
public record DeviceStatusModel(
        DeviceKind kind,
        String displayName,
        // binding
        boolean hasBound, String boundDim, int boundX, int boundY, int boundZ, boolean gridReachable,
        boolean appFluxOnline,
        // energy
        long storedFe, long capacityFe,
        // overload
        int dynamicLoad, int overloadCap,
        int lockState, int lockValue, // 0=OK, 1=debt(ticks), 2=locked(remaining)
        boolean powered,
        // modules
        List<ModuleInfo> modules,
        int moduleSlotCount,
        // railgun specific
        boolean terrainDestruction, boolean aoeEnabled, boolean pvpLock, boolean terrainDestructionAllowed
) {
    public record ModuleInfo(String id, String name, boolean enabled, int load) {
    }

    public static final DeviceStatusModel EMPTY = new DeviceStatusModel(
            DeviceKind.OVERLOAD_HELMET, "", false, "", 0, 0, 0, false, false,
            0, 0, 0, 0, 0, 0, false, List.of(), 0,
            false, false, false, false);

    /** Build status snapshot from an armor stack worn by the player. */
    public static DeviceStatusModel fromArmorStack(ItemStack armor, ServerPlayer player) {
        if (armor == null || armor.isEmpty() || !(armor.getItem() instanceof BaseOverloadArmorItem armorItem)) {
            return EMPTY;
        }
        ArmorPart part = armorItem.armorPart();
        DeviceKind kind = armorItem.deviceKind();
        String name = armor.getHoverName().getString();

        // Binding
        GlobalPos boundPos = ArmorNetworkBinding.INSTANCE.getBoundPos(armor);
        boolean hasBound = boundPos != null;
        String boundDim = hasBound ? boundPos.dimension().location().toString() : "";
        int bx = hasBound ? boundPos.pos().getX() : 0;
        int by = hasBound ? boundPos.pos().getY() : 0;
        int bz = hasBound ? boundPos.pos().getZ() : 0;
        var resolve = ArmorNetworkBinding.INSTANCE.resolve(armor, player);
        boolean gridReachable = resolve.success();
        boolean appFlux = AppFluxBridge.isAvailable();

        // Energy
        long stored = ArmorEnergyBuffer.read(armor);
        long capacity = ArmorEnergyBuffer.capacity(armor);

        // Overload
        var armorId = OverloadArmorState.getArmorId(armor);
        int dynamicLoad = 0;
        int lockStateVal = 0;
        int lockValue = 0;
        if (armorId != null) {
            var runtime = OverloadRuntime.get(armorId);
            dynamicLoad = runtime.currentLoad();
            LockState ls = runtime.dynamics().state();
            if (ls instanceof LockState.Locked locked) {
                lockStateVal = 2;
                lockValue = locked.ticksRemaining();
            } else if (ls instanceof LockState.Debt debt) {
                lockStateVal = 1;
                lockValue = debt.ticksRemaining();
            }
        }
        int cap = part.dynamicCap();
        boolean powered = stored > 0 || gridReachable;

        // Modules
        var submodules = OverloadArmorState.collectSubmodules(armor, player.registryAccess());
        List<ModuleInfo> modules = new ArrayList<>();
        for (var sub : submodules) {
            boolean enabled = OverloadArmorState.isSubmoduleEnabled(armor, sub);
            int load = OverloadArmorState.getSubmoduleDynamicLoad(armor, sub);
            modules.add(new ModuleInfo(sub.id(), sub.id(), enabled, load));
        }

        return new DeviceStatusModel(
                kind, name, hasBound, boundDim, bx, by, bz, gridReachable, appFlux,
                stored, capacity, dynamicLoad, cap, lockStateVal, lockValue, powered,
                modules, part.moduleSlotCount(),
                false, false, false, false);
    }

    /** Build status snapshot from a railgun stack held by the player. */
    public static DeviceStatusModel fromRailgunStack(ItemStack railgun, ServerPlayer player) {
        if (railgun == null || railgun.isEmpty()) {
            return EMPTY;
        }
        String name = railgun.getHoverName().getString();

        // Binding
        GlobalPos boundPos = RailgunBinding.getBoundPos(railgun);
        boolean hasBound = boundPos != null;
        String boundDim = hasBound ? boundPos.dimension().location().toString() : "";
        int bx = hasBound ? boundPos.pos().getX() : 0;
        int by = hasBound ? boundPos.pos().getY() : 0;
        int bz = hasBound ? boundPos.pos().getZ() : 0;
        var resolve = RailgunBinding.resolve(railgun, player);
        boolean gridReachable = resolve.success();
        boolean appFlux = AppFluxBridge.isAvailable();

        // Energy
        long stored = RailgunEnergyBuffer.read(railgun);
        long capacity = RailgunEnergyBuffer.capacity(railgun);

        // Overload
        int dynamicLoad = RailgunOverloadBudget.INSTANCE.currentLoad(railgun);
        int cap = RailgunOverloadBudget.INSTANCE.budgetCap(railgun);
        LockState ls = RailgunOverloadBudget.INSTANCE.lockState(railgun);
        int lockStateVal = 0;
        int lockValueVal = 0;
        if (ls instanceof LockState.Locked locked) {
            lockStateVal = 2;
            lockValueVal = locked.ticksRemaining();
        } else if (ls instanceof LockState.Debt debt) {
            lockStateVal = 1;
            lockValueVal = debt.ticksRemaining();
        }
        boolean powered = stored > 0 || gridReachable;

        // Modules
        var entries = RailgunModuleStorage.entryData(railgun);
        List<ModuleInfo> modules = new ArrayList<>();
        if (entries.hasCore()) {
            modules.add(new ModuleInfo("core", "Core", true, 0));
        }
        if (entries.computeCount() > 0) {
            modules.add(new ModuleInfo("compute", "Compute x" + entries.computeCount(), true, 0));
        }
        if (entries.accelerationCount() > 0) {
            modules.add(new ModuleInfo("acceleration", "Acceleration x" + entries.accelerationCount(), true, 0));
        }
        if (entries.hasOverloadExecution()) {
            modules.add(new ModuleInfo("overload_execution", "Overload Execution", true, 0));
        }

        // Settings
        RailgunSettings settings = railgun.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        boolean terrainAllowed = AE2LTCommonConfig.railgunTerrainDestructionEnabled();

        return new DeviceStatusModel(
                DeviceKind.RAILGUN, name, hasBound, boundDim, bx, by, bz, gridReachable, appFlux,
                stored, capacity, dynamicLoad, cap, lockStateVal, lockValueVal, powered,
                modules, 5,
                terrainAllowed && settings.terrainDestruction(), settings.aoeEnabled(), settings.pvpLock(),
                terrainAllowed);
    }
}
