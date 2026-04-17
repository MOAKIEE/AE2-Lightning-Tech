package com.moakiee.ae2lt.overload.armor.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

/**
 * Runtime registry of every submodule instance the armor has encountered this session. We rely on
 * a live-instance cache (rather than a static catalog of default modules) so that:
 *
 * <ul>
 *   <li>Newly-crafted armor ships with zero modules — the baked-in terminal proxy + energy run is
 *       the entire default feature set;</li>
 *   <li>{@link #findSubmoduleById(String)} can still return a living instance after the providing
 *       item is removed, enabling the reconciler to fire {@code onUninstalled};</li>
 *   <li>Third-party addon items implementing {@link OverloadArmorSubmoduleItem} are registered
 *       automatically the first time we collect from them — no boilerplate registration call.</li>
 * </ul>
 */
public final class OverloadArmorFeatureCatalog {
    private static final Map<String, OverloadArmorSubmodule> KNOWN_SUBMODULES = new ConcurrentHashMap<>();

    private OverloadArmorFeatureCatalog() {
    }

    public static void registerSubmodule(OverloadArmorSubmodule submodule) {
        if (submodule == null || submodule.id().isBlank()) {
            return;
        }
        KNOWN_SUBMODULES.putIfAbsent(submodule.id(), submodule);
    }

    @Nullable
    public static OverloadArmorSubmodule findSubmoduleById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return KNOWN_SUBMODULES.get(id);
    }

    /**
     * Gathers every submodule provided by the given module-slot stacks. The order of the returned
     * list follows the slot order so consumers (base-overload cap admission, UI) can rely on a
     * deterministic ordering.
     */
    public static List<OverloadArmorSubmodule> collectSubmodules(List<ItemStack> moduleStacks) {
        var submodules = new LinkedHashMap<String, OverloadArmorSubmodule>();
        for (var stack : moduleStacks) {
            collectFromItem(stack, submodules);
        }
        return List.copyOf(submodules.values());
    }

    /**
     * @deprecated only kept so legacy JEI / diagnostics code keeps building; new call sites should
     *     pass module-slot stacks via {@link #collectSubmodules(List)} instead.
     */
    @Deprecated(forRemoval = true)
    public static List<OverloadArmorFeature> collect(ItemStack core, ItemStack buffer, ItemStack terminal) {
        return collectSubmodules(List.of(core, buffer, terminal))
                .stream()
                .filter(OverloadArmorFeature.class::isInstance)
                .map(OverloadArmorFeature.class::cast)
                .collect(Collectors.toUnmodifiableList());
    }

    @SuppressWarnings("deprecation")
    private static void collectFromItem(
            ItemStack stack,
            LinkedHashMap<String, OverloadArmorSubmodule> submodules
    ) {
        if (stack.isEmpty() || !(stack.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
            return;
        }

        // SlotType is preserved only because some third-party submodule items rely on it. For the
        // armor's own module slots we pass null via the overload below; providers that need the
        // legacy enum continue to work via the default bridge implementation.
        provider.collectSubmodules(stack, submodule -> add(submodules, submodule));
    }

    private static void add(
            LinkedHashMap<String, OverloadArmorSubmodule> submodules,
            OverloadArmorSubmodule submodule
    ) {
        if (submodule == null || submodule.id().isBlank()) {
            return;
        }
        var resolved = Objects.requireNonNull(submodule);
        submodules.putIfAbsent(resolved.id(), resolved);
        KNOWN_SUBMODULES.putIfAbsent(resolved.id(), resolved);
    }
}
