package com.moakiee.ae2lt.overload.armor.module;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

/**
 * Implemented by items that can sit in an armor module slot and contribute one or more
 * {@link OverloadArmorSubmodule} instances to the armor's runtime. Providers should not cache the
 * submodule instance inside the ItemStack — {@link OverloadArmorFeatureCatalog} maintains a
 * process-wide cache keyed by submodule id for stable lookup after removal.
 *
 * <p>Also implements {@link OverloadDeviceModuleItem} so the unified device layer can bind
 * armor module items into capability-driven services. Capability declaration defaults to empty;
 * Phase 5 submodules (reflect / dash / flight / ...) override {@link #capabilities} to expose
 * their per-stack contribution.
 */
public interface OverloadArmorSubmoduleItem extends OverloadDeviceModuleItem {

    Set<DeviceKind> ACCEPTS = Set.of(DeviceKind.OVERLOAD_ARMOR);

    @Override
    default Set<DeviceKind> acceptableDevices() {
        return ACCEPTS;
    }

    @Override
    default DeviceSlotType acceptableSlot() {
        return DeviceSlotType.SUBMODULE;
    }

    @Override
    default List<DeviceCapability> capabilities(ItemStack stack) {
        return List.of();
    }

    /**
     * Primary collection hook used by the armor's module slots. Emit every submodule provided by
     * {@code stack} through {@code output}.
     */
    default void collectSubmodules(ItemStack stack, Consumer<OverloadArmorSubmodule> output) {
        // Bridge for legacy implementations that still key off the pre-refactor SlotType enum.
        collectSubmodules(stack, SlotType.MODULE, output);
    }

    /**
     * @deprecated legacy signature from when core/buffer/terminal slots still produced submodules.
     *     New code should override {@link #collectSubmodules(ItemStack, Consumer)} instead and
     *     ignore {@link SlotType}. Retained so already-compiled addon jars keep loading; intend
     *     to delete once there are no external call sites.
     */
    @Deprecated(forRemoval = false)
    default void collectSubmodules(ItemStack stack, SlotType slotType, Consumer<OverloadArmorSubmodule> output) {
    }

    @Deprecated(forRemoval = false)
    default void collectFeatures(ItemStack stack, SlotType slotType, Consumer<OverloadArmorFeature> output) {
        collectSubmodules(stack, slotType, submodule -> {
            if (submodule instanceof OverloadArmorFeature feature) {
                output.accept(feature);
            }
        });
    }

    enum SlotType {
        CORE,
        BUFFER,
        TERMINAL,
        MODULE
    }
}

