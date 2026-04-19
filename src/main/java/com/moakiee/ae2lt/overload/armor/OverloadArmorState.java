package com.moakiee.ae2lt.overload.armor;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;

import com.moakiee.ae2lt.item.LightningStorageComponentItem;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorFeature;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorFeatureCatalog;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.registry.ModItems;

public final class OverloadArmorState {
    public static final int SLOT_CORE = 0;
    public static final int SLOT_BUFFER = 1;
    public static final int SLOT_TERMINAL = 2;
    public static final int SLOT_COUNT = 3;

    /**
     * Maximum number of <em>distinct module types</em> an armor may have installed simultaneously.
     * Each entry in the persisted module list corresponds to one submodule type; the stack's count
     * is the installed amount (stacking behaviour), so this is NOT the total number of module
     * instances. The real install-time limit is the sum of
     * {@code perInstanceIdle × installedAmount} over every entry staying at or below the armor's
     * {@linkplain #getBaseOverload base overload} budget, enforced by {@link #canInstallModule}.
     * The 32 cap is purely a sanity upper bound on distinct types to keep UI / sync bounded.
     */
    public static final int MAX_MODULE_TYPES = 32;

    private static final String TAG_ROOT = "OverloadArmor";
    private static final String TAG_ARMOR_ID = "ArmorId";
    private static final String TAG_STORED_ENERGY = "StoredEnergy";
    private static final String TAG_UNPAID_ENERGY = "UnpaidEnergy";
    private static final String TAG_DEBT_TICKS = "DebtTicks";
    private static final String TAG_LOCKED_TICKS = "LockedTicks";
    private static final String TAG_TERMINAL_CONTENT_VERSION = "TerminalContentVersion";
    private static final String TAG_TERMINAL_SESSION_VERSION = "TerminalSessionVersion";
    private static final String TAG_FEATURE_TOGGLES = "FeatureToggles";
    private static final String TAG_SUBMODULE_DATA = "SubmoduleData";
    private static final String TAG_SUBMODULE_RUNTIME = "SubmoduleRuntime";
    private static final String TAG_INSTALLED_SUBMODULES = "InstalledSubmodules";
    private static final String TAG_MODULE_SLOTS = "ModuleSlots";
    private static final String TAG_RUNTIME_ACTIVE = "Active";
    private static final String TAG_RUNTIME_DYNAMIC_LOAD = "DynamicLoad";
    private static final String[] SLOT_KEYS = {"Core", "Buffer", "Terminal"};

    private static final int EQUIPPED_IDLE_LOAD = 8;
    private static final int BUFFER_IDLE_LOAD = 4;
    private static final int ULTIMATE_CORE_OVERLOAD = 128;
    public static final int LOCK_TRIGGER_TICKS = 10;
    public static final int LOCK_DURATION_TICKS = 20 * 60 * 10;

    private OverloadArmorState() {
    }

    public static ItemStack[] loadSlots(ItemStack armor, HolderLookup.Provider registries) {
        var result = new ItemStack[SLOT_COUNT];
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            result[slot] = getSlot(armor, registries, slot);
        }
        return result;
    }

    public static ItemStack getSlot(ItemStack armor, HolderLookup.Provider registries, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }

        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }

        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(SLOT_KEYS[slot], CompoundTag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }

        return ItemStack.parseOptional(registries, armorTag.getCompound(SLOT_KEYS[slot]).copy());
    }

    public static void saveSlots(ItemStack armor, HolderLookup.Provider registries, ItemStack[] slots) {
        var previousTerminal = getSlot(armor, registries, SLOT_TERMINAL);
        var nextTerminal = slots.length > SLOT_TERMINAL && slots[SLOT_TERMINAL] != null
                ? slots[SLOT_TERMINAL]
                : ItemStack.EMPTY;

        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();

            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                var stack = slot < slots.length && slots[slot] != null ? slots[slot] : ItemStack.EMPTY;
                if (stack.isEmpty()) {
                    armorTag.remove(SLOT_KEYS[slot]);
                } else {
                    armorTag.put(SLOT_KEYS[slot], stack.saveOptional(registries));
                }
            }

            rootTag.put(TAG_ROOT, armorTag);
        });

        if (!ItemStack.matches(previousTerminal, nextTerminal)) {
            incrementTerminalContentVersion(armor);
        }
    }

    public static void setSlot(ItemStack armor, HolderLookup.Provider registries, int slot, ItemStack stack) {
        var slots = loadSlots(armor, registries);
        if (slot >= 0 && slot < SLOT_COUNT) {
            slots[slot] = stack.copy();
            saveSlots(armor, registries, slots);
        }
    }

    // ── Module list (moduleId → installed amount) ──────────────────────────────────────────
    //
    // Modules live in a dynamic list of ItemStacks persisted under TAG_MODULE_SLOTS. Each entry
    // is a single-type entry; the stack's count IS the installed amount of that submodule type.
    // Identity = the item class / id, so two stacks of the same submodule item merge into one
    // entry at install time. This gives Mekanism-style "×N" stacking with an arbitrary variety
    // of installed types (capped at {@link #MAX_MODULE_TYPES}).
    //
    // API summary:
    //   loadModuleStacks(armor, registries)           — compact list of installed types
    //   getInstalledAmount(armor, registries, id)     — count of a specific submodule type
    //   installOneModule(armor, registries, candidate) — install one instance, merge if same type
    //   uninstallOneModule(armor, registries, id)     — extract one instance to hand out to player
    //   hasAnyInstalledModule(armor, registries)      — any entry present? (pins the core)
    //   computeTotalIdleOverload(armor, registries)   — full idle budget used by all installs

    /**
     * Loads the armor's module list as a compact list of distinct-type stacks. Each stack's
     * {@link ItemStack#getCount()} is the number of installed instances for that type. Empty
     * entries are filtered out so indices line up 1:1 with installed types, making UI iteration
     * trivial.
     */
    public static List<ItemStack> loadModuleStacks(ItemStack armor, HolderLookup.Provider registries) {
        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return List.of();
        }
        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_MODULE_SLOTS, CompoundTag.TAG_LIST)) {
            return List.of();
        }
        var list = armorTag.getList(TAG_MODULE_SLOTS, CompoundTag.TAG_COMPOUND);
        var result = new java.util.ArrayList<ItemStack>(list.size());
        for (int index = 0; index < list.size(); index++) {
            var entry = list.getCompound(index);
            if (entry.isEmpty()) {
                continue;
            }
            var stack = ItemStack.parseOptional(registries, entry);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * Persists the given compact list (typically after mutate-in-place through the helpers below).
     * Trims empty/zero-count entries and collapses duplicates of the same submodule id so the
     * invariant "one entry per type" is maintained even if callers mis-behave.
     */
    private static void saveModuleStacks(
            ItemStack armor,
            HolderLookup.Provider registries,
            List<ItemStack> stacks
    ) {
        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();
            var out = new net.minecraft.nbt.ListTag();
            // Merge same-id entries so the list always has one row per type, regardless of caller.
            var merged = new java.util.LinkedHashMap<String, ItemStack>();
            for (var stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                String id = resolveSubmoduleId(stack);
                if (id.isBlank()) continue;
                var existing = merged.get(id);
                if (existing == null) {
                    merged.put(id, stack.copy());
                } else {
                    existing.grow(stack.getCount());
                }
            }
            int written = 0;
            for (var stack : merged.values()) {
                if (written >= MAX_MODULE_TYPES) break;
                if (stack.isEmpty()) continue;
                out.add((CompoundTag) stack.saveOptional(registries));
                written++;
            }
            if (out.isEmpty()) {
                armorTag.remove(TAG_MODULE_SLOTS);
            } else {
                armorTag.put(TAG_MODULE_SLOTS, out);
            }
            rootTag.put(TAG_ROOT, armorTag);
        });
    }

    /**
     * Resolves the canonical submodule id for an item stack by asking the item's own
     * {@link OverloadArmorSubmoduleItem#collectSubmodules} callback for the first submodule it
     * declares. Returns "" if the stack isn't a recognized submodule item. We identify modules by
     * their submodule id (rather than the Item instance) to keep config persistence stable across
     * refactors where one item might forward to multiple submodules.
     */
    private static String resolveSubmoduleId(ItemStack stack) {
        if (stack.isEmpty()
                || !(stack.getItem() instanceof com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem provider)) {
            return "";
        }
        var ref = new String[]{""};
        provider.collectSubmodules(stack, submodule -> {
            if (submodule != null && !submodule.id().isBlank() && ref[0].isEmpty()) {
                ref[0] = submodule.id();
            }
        });
        return ref[0];
    }

    /**
     * Returns the installed count of the given submodule type, or 0 if not installed.
     */
    public static int getInstalledAmount(
            ItemStack armor,
            HolderLookup.Provider registries,
            String submoduleId
    ) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return 0;
        }
        for (var stack : loadModuleStacks(armor, registries)) {
            if (submoduleId.equals(resolveSubmoduleId(stack))) {
                return stack.getCount();
            }
        }
        return 0;
    }

    /**
     * Installs a single instance of {@code candidate} into the armor, merging with an existing
     * same-type entry when present. Returns {@code true} iff the installation succeeded. Fails if:
     * <ul>
     *   <li>the armor has no core installed (modules require a core);</li>
     *   <li>the resulting idle sum would exceed the armor's base overload budget;</li>
     *   <li>the candidate is not a submodule item;</li>
     *   <li>the module-type cap {@link #MAX_MODULE_TYPES} is already hit with a new type.</li>
     * </ul>
     */
    public static boolean installOneModule(
            ItemStack armor,
            HolderLookup.Provider registries,
            ItemStack candidate
    ) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (!canInstallModule(armor, registries, candidate)) return false;

        String id = resolveSubmoduleId(candidate);
        if (id.isBlank()) return false;

        var stacks = new java.util.ArrayList<>(loadModuleStacks(armor, registries));
        boolean merged = false;
        for (var stack : stacks) {
            if (id.equals(resolveSubmoduleId(stack))) {
                stack.grow(1);
                merged = true;
                break;
            }
        }
        if (!merged) {
            if (stacks.size() >= MAX_MODULE_TYPES) {
                return false;
            }
            var unit = candidate.copyWithCount(1);
            stacks.add(unit);
        }
        saveModuleStacks(armor, registries, stacks);
        return true;
    }

    /**
     * Extracts a single instance of the given submodule type from the armor, returning the
     * detached stack (count=1) to hand out to the player. Returns {@link ItemStack#EMPTY} if the
     * type is not installed. When the type's count drops to zero the entry is pruned from the
     * list entirely.
     */
    public static ItemStack uninstallOneModule(
            ItemStack armor,
            HolderLookup.Provider registries,
            String submoduleId
    ) {
        if (submoduleId == null || submoduleId.isBlank()) return ItemStack.EMPTY;

        var stacks = new java.util.ArrayList<>(loadModuleStacks(armor, registries));
        for (int index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (submoduleId.equals(resolveSubmoduleId(stack))) {
                var detached = stack.copyWithCount(1);
                if (stack.getCount() <= 1) {
                    stacks.remove(index);
                } else {
                    stack.shrink(1);
                }
                saveModuleStacks(armor, registries, stacks);
                return detached;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Extracts every installed instance of the given submodule type in one shot. Returns a stack
     * whose count equals the previous installed amount (may exceed the item's maxStackSize — the
     * caller is expected to split before handing to a player inventory). Returns
     * {@link ItemStack#EMPTY} if the type is not installed.
     */
    public static ItemStack uninstallAllOfType(
            ItemStack armor,
            HolderLookup.Provider registries,
            String submoduleId
    ) {
        if (submoduleId == null || submoduleId.isBlank()) return ItemStack.EMPTY;

        var stacks = new java.util.ArrayList<>(loadModuleStacks(armor, registries));
        for (int index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (submoduleId.equals(resolveSubmoduleId(stack))) {
                var detached = stack.copy();
                stacks.remove(index);
                saveModuleStacks(armor, registries, stacks);
                return detached;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns true if any module is currently installed (i.e. the list has at least one entry
     * with count &gt; 0). Used to gate core removal — even a single installed module pins the
     * core in place and forces a swap-only semantic on the core slot.
     */
    public static boolean hasAnyInstalledModule(ItemStack armor, HolderLookup.Provider registries) {
        return !loadModuleStacks(armor, registries).isEmpty();
    }

    /**
     * Sums idle overload over every installed module, scaling per-instance idle by the installed
     * amount. Enabled/disabled state is ignored — idle is a property of the physical installation,
     * not of the runtime toggle, so a disabled module still reserves its idle slice.
     */
    public static int computeTotalIdleOverload(ItemStack armor, HolderLookup.Provider registries) {
        int total = 0;
        for (var stack : loadModuleStacks(armor, registries)) {
            total += computeStackIdleOverload(armor, stack);
        }
        return total;
    }

    /**
     * Idle overload contribution of a single list entry: per-instance idle × installed amount.
     * Uses the catalog to resolve the submodule instance from the item. Safe to call on either
     * logical side — idle overload is a static property of the submodule.
     */
    public static int computeStackIdleOverload(ItemStack armor, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int perInstance = 0;
        for (var submodule : OverloadArmorFeatureCatalog.collectSubmodules(List.of(stack))) {
            perInstance += Math.max(submodule.getIdleOverloaded(null, Dist.CLIENT, armor), 0);
        }
        return perInstance * stack.getCount();
    }

    public static int getBaseOverload(ItemStack armor, HolderLookup.Provider registries) {
        return getBaseOverloadFor(getSlot(armor, registries, SLOT_CORE));
    }

    /**
     * Computes the base overload budget that a given core stack would provide. Kept separate from
     * {@link #getBaseOverload(ItemStack, HolderLookup.Provider)} so the workbench can check a
     * candidate core before committing it to the armor's core slot, which is how we block the
     * "install modules on a high-tier core, then swap to a low-tier core" exploit.
     */
    public static int getBaseOverloadFor(ItemStack core) {
        if (core == null || core.isEmpty()) {
            return 0;
        }
        return core.is(ModItems.ULTIMATE_OVERLOAD_CORE.get()) ? ULTIMATE_CORE_OVERLOAD : 0;
    }

    /**
     * Returns true iff installing one instance of {@code candidate} into the armor would keep the
     * sum of installed-module idle overload at or under the armor's current base overload. Used
     * both by the workbench input slot's {@code mayPlace} (client hint) and by the block entity's
     * auto-consume loop (server enforcement).
     */
    public static boolean canInstallModule(
            ItemStack armor,
            HolderLookup.Provider registries,
            ItemStack candidate
    ) {
        // A module only makes sense as an extension of an installed core: without a core there is
        // no base overload budget and no runtime host, so even zero-idle modules are rejected to
        // keep the "modules always live under a core" invariant. This also avoids the degenerate
        // `0 + 0 <= 0` pass that let the player reach "no core, modules installed" states.
        if (getSlot(armor, registries, SLOT_CORE).isEmpty()) {
            return false;
        }
        if (candidate == null || candidate.isEmpty()) return false;
        if (!(candidate.getItem() instanceof com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem)) {
            return false;
        }
        // Per-type install cap: a submodule that declares a max-install count blocks further
        // installs of its own id once the cap is reached, regardless of the global idle budget.
        // Iterated per-submodule because one item may emit multiple submodules (rare but legal).
        for (var submodule : OverloadArmorFeatureCatalog.collectSubmodules(List.of(candidate))) {
            int cap = submodule.getMaxInstallAmount();
            if (cap > 0 && getInstalledAmount(armor, registries, submodule.id()) >= cap) {
                return false;
            }
        }
        int baseOverload = getBaseOverload(armor, registries);
        int current = computeTotalIdleOverload(armor, registries);
        // Per-instance idle only: installing one adds exactly one unit's idle.
        int perInstance = 0;
        for (var submodule : OverloadArmorFeatureCatalog.collectSubmodules(List.of(candidate))) {
            perInstance += Math.max(submodule.getIdleOverloaded(null, Dist.CLIENT, armor), 0);
        }
        return current + perInstance <= baseOverload;
    }

    /**
     * Returns the declared per-type install cap of the given submodule id, or {@code 0} if the
     * submodule is uncapped (limited only by the idle-overload budget). Used by the UI to render
     * "×N/Max" badges.
     */
    public static int getSubmoduleMaxInstallAmount(String submoduleId) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return 0;
        }
        var submodule = OverloadArmorFeatureCatalog.findSubmoduleById(submoduleId);
        return submodule != null ? Math.max(submodule.getMaxInstallAmount(), 0) : 0;
    }

    /**
     * Convenience: returns the declared per-type install cap of the first submodule emitted by
     * the given stack's provider, or {@code 0} if uncapped / not a submodule item. Used by the
     * workbench screen to render the "×N/Max" badge without having to re-implement id lookup.
     */
    public static int getSubmoduleMaxInstallAmountForStack(ItemStack stack) {
        return getSubmoduleMaxInstallAmount(resolveSubmoduleId(stack));
    }

    /**
     * Anti-downgrade guard: returns true only when the candidate core still has enough base
     * overload budget to cover every currently-installed module's idle load. Swapping to a weaker
     * core is allowed iff the installed idle sum still fits; otherwise the slot refuses the
     * insertion and the player must uninstall modules first. Empty cores always pass (removing
     * the core just zeroes the budget without unloading modules — activation fails at runtime
     * naturally because core.isEmpty() kills {@link #canSubmoduleBeActive}).
     */
    public static boolean canInstallCore(
            ItemStack armor,
            HolderLookup.Provider registries,
            ItemStack candidateCore
    ) {
        if (candidateCore == null || candidateCore.isEmpty()) {
            return true;
        }
        int candidateBase = getBaseOverloadFor(candidateCore);
        int installedIdle = computeTotalIdleOverload(armor, registries);
        return installedIdle <= candidateBase;
    }

    public static UUID ensureArmorId(ItemStack armor) {
        var existing = getArmorId(armor);
        if (existing != null) {
            return existing;
        }

        var created = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();
            armorTag.putUUID(TAG_ARMOR_ID, created);
            rootTag.put(TAG_ROOT, armorTag);
        });
        return created;
    }

    @Nullable
    public static UUID getArmorId(ItemStack armor) {
        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return null;
        }

        var armorTag = root.getCompound(TAG_ROOT);
        return armorTag.hasUUID(TAG_ARMOR_ID) ? armorTag.getUUID(TAG_ARMOR_ID) : null;
    }

    public static List<OverloadArmorSubmodule> collectSubmodules(ItemStack armor, HolderLookup.Provider registries) {
        return OverloadArmorFeatureCatalog.collectSubmodules(
                loadModuleStacks(armor, registries));
    }

    @Nullable
    public static OverloadArmorSubmodule getSubmodule(ItemStack armor, HolderLookup.Provider registries, int index) {
        var submodules = collectSubmodules(armor, registries);
        return index >= 0 && index < submodules.size() ? submodules.get(index) : null;
    }

    public static boolean isSubmoduleEnabled(ItemStack armor, OverloadArmorSubmodule submodule) {
        return isSubmoduleEnabled(armor, submodule.id(), submodule.defaultEnabled());
    }

    public static boolean isSubmoduleEnabled(ItemStack armor, String submoduleId, boolean defaultEnabled) {
        if (submoduleId.isBlank()) {
            return defaultEnabled;
        }

        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return defaultEnabled;
        }

        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_FEATURE_TOGGLES, CompoundTag.TAG_COMPOUND)) {
            return defaultEnabled;
        }

        var featureToggles = armorTag.getCompound(TAG_FEATURE_TOGGLES);
        return featureToggles.contains(submoduleId, CompoundTag.TAG_BYTE)
                ? featureToggles.getBoolean(submoduleId)
                : defaultEnabled;
    }

    public static void setSubmoduleEnabled(ItemStack armor, OverloadArmorSubmodule submodule, boolean enabled) {
        setSubmoduleEnabled(armor, submodule.id(), enabled, submodule.defaultEnabled());
    }

    public static void setSubmoduleEnabled(
            ItemStack armor,
            String submoduleId,
            boolean enabled,
            boolean defaultEnabled
    ) {
        if (submoduleId.isBlank()) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();
            var featureToggles = armorTag.contains(TAG_FEATURE_TOGGLES, CompoundTag.TAG_COMPOUND)
                    ? armorTag.getCompound(TAG_FEATURE_TOGGLES)
                    : new CompoundTag();

            if (enabled == defaultEnabled) {
                featureToggles.remove(submoduleId);
            } else {
                featureToggles.putBoolean(submoduleId, enabled);
            }

            if (featureToggles.isEmpty()) {
                armorTag.remove(TAG_FEATURE_TOGGLES);
            } else {
                armorTag.put(TAG_FEATURE_TOGGLES, featureToggles);
            }
            rootTag.put(TAG_ROOT, armorTag);
        });
    }

    public static CompoundTag getSubmoduleData(ItemStack armor, OverloadArmorSubmodule submodule) {
        var rawData = getStoredSubmoduleData(armor, submodule.id());
        var normalized = submodule.loadData(armor, rawData.copy());
        if (!normalized.equals(rawData)) {
            setStoredSubmoduleData(armor, submodule.id(), normalized);
        }
        return normalized.copy();
    }

    public static void setSubmoduleData(ItemStack armor, OverloadArmorSubmodule submodule, CompoundTag data) {
        setStoredSubmoduleData(armor, submodule.id(), submodule.saveData(armor, data.copy()));
    }

    public static int getSubmoduleDynamicLoad(ItemStack armor, OverloadArmorSubmodule submodule) {
        return getStoredSubmoduleDynamicLoad(armor, submodule.id());
    }

    public static boolean isSubmoduleActive(
            ItemStack armor,
            OverloadArmorSubmodule submodule,
            HolderLookup.Provider registries,
            boolean equipped
    ) {
        return canSubmoduleBeActive(armor, submodule, registries, equipped);
    }

    /**
     * Reads the persisted runtime active flag written by
     * {@link #syncSubmoduleActiveState(Player, ItemStack, HolderLookup.Provider, boolean, Dist)}.
     */
    public static boolean isSubmoduleRuntimeActive(ItemStack armor, String submoduleId) {
        return getSubmoduleRuntime(armor, submoduleId).active();
    }

    /**
     * Reads the persisted dynamic load as last reported by
     * {@link OverloadArmorSubmodule#tickActive(Player, Dist, ItemStack)}.
     */
    public static int getSubmoduleDynamicLoadFor(ItemStack armor, String submoduleId) {
        return getStoredSubmoduleDynamicLoad(armor, submoduleId);
    }

    /**
     * Returns true while the submodule's providing item is physically installed in the armor.
     */
    public static boolean isSubmoduleInstalled(ItemStack armor, String submoduleId) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return false;
        }

        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return false;
        }

        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_LIST)) {
            return false;
        }

        var list = armorTag.getList(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            if (submoduleId.equals(list.getString(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Diffs the currently-present submodules against the persisted installed set and fires
     * {@link OverloadArmorSubmodule#onInstalled(Player, Dist, ItemStack)} /
     * {@link OverloadArmorSubmodule#onUninstalled(Player, Dist, ItemStack)} for the delta. Safe to
     * call from both logical sides; submodules must branch on {@code dist} when performing side-
     * specific work.
     */
    public static void reconcileInstalledSubmodules(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            Dist dist
    ) {
        var present = collectSubmodules(armor, registries);
        var presentIds = new java.util.LinkedHashSet<String>();
        var presentById = new java.util.LinkedHashMap<String, OverloadArmorSubmodule>();
        for (var submodule : present) {
            if (submodule.id().isBlank()) {
                continue;
            }
            presentIds.add(submodule.id());
            presentById.putIfAbsent(submodule.id(), submodule);
        }

        var storedIds = readInstalledSubmoduleIds(armor);

        // Fire onDeactivated (if currently active) + onUninstalled for removed submodules. We
        // guarantee deactivation precedes uninstall so modules can rely on a single teardown path:
        // implementing only onDeactivated is sufficient in the common case, uninstall is for edge
        // cases that genuinely differ from a transient deactivation.
        for (var storedId : storedIds) {
            if (presentIds.contains(storedId)) {
                continue;
            }
            var lookup = OverloadArmorFeatureCatalog.findSubmoduleById(storedId);
            if (lookup != null) {
                if (getSubmoduleRuntime(armor, storedId).active()) {
                    lookup.onDeactivated(player, dist, armor);
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                            && getArmorId(armor) != null) {
                        com.moakiee.ae2lt.network.SubmoduleLifecyclePacket.broadcast(
                                serverPlayer, getArmorId(armor), storedId, false);
                    }
                }
                lookup.onUninstalled(player, dist, armor);
            }
            clearSubmoduleRuntime(armor, storedId);
        }

        // Fire onInstalled for newly-present submodules.
        for (var id : presentIds) {
            if (storedIds.contains(id)) {
                continue;
            }
            var submodule = presentById.get(id);
            if (submodule != null) {
                submodule.onInstalled(player, dist, armor);
            }
        }

        if (!storedIds.equals(presentIds)) {
            writeInstalledSubmoduleIds(armor, presentIds);
        }
    }

    private static java.util.LinkedHashSet<String> readInstalledSubmoduleIds(ItemStack armor) {
        var result = new java.util.LinkedHashSet<String>();
        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return result;
        }

        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_LIST)) {
            return result;
        }

        var list = armorTag.getList(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            var id = list.getString(index);
            if (!id.isBlank()) {
                result.add(id);
            }
        }
        return result;
    }

    private static void writeInstalledSubmoduleIds(ItemStack armor, java.util.Collection<String> ids) {
        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();
            if (ids.isEmpty()) {
                armorTag.remove(TAG_INSTALLED_SUBMODULES);
            } else {
                var list = new net.minecraft.nbt.ListTag();
                for (var id : ids) {
                    list.add(net.minecraft.nbt.StringTag.valueOf(id));
                }
                armorTag.put(TAG_INSTALLED_SUBMODULES, list);
            }
            rootTag.put(TAG_ROOT, armorTag);
        });
    }

    private static void clearSubmoduleRuntime(ItemStack armor, String submoduleId) {
        if (submoduleId.isBlank()) {
            return;
        }
        UUID armorId = getArmorId(armor);
        if (armorId != null) {
            SUBMODULE_RUNTIME_CACHE.remove(cacheKey(armorId, submoduleId));
            SERVER_ACTIVE_CACHE.remove(cacheKey(armorId, submoduleId));
            CLIENT_ACTIVE_CACHE.remove(cacheKey(armorId, submoduleId));
        }
        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            if (!rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
                return;
            }
            var armorTag = rootTag.getCompound(TAG_ROOT);
            if (!armorTag.contains(TAG_SUBMODULE_RUNTIME, CompoundTag.TAG_COMPOUND)) {
                return;
            }
            var runtimeData = armorTag.getCompound(TAG_SUBMODULE_RUNTIME);
            runtimeData.remove(submoduleId);
            if (runtimeData.isEmpty()) {
                armorTag.remove(TAG_SUBMODULE_RUNTIME);
            } else {
                armorTag.put(TAG_SUBMODULE_RUNTIME, runtimeData);
            }
            rootTag.put(TAG_ROOT, armorTag);
        });
    }

    public static boolean isFeatureEnabled(ItemStack armor, OverloadArmorFeature feature) {
        return isSubmoduleEnabled(armor, feature);
    }

    public static boolean isFeatureEnabled(ItemStack armor, String featureId, boolean defaultEnabled) {
        return isSubmoduleEnabled(armor, featureId, defaultEnabled);
    }

    public static void setFeatureEnabled(ItemStack armor, OverloadArmorFeature feature, boolean enabled) {
        setSubmoduleEnabled(armor, feature, enabled);
    }

    public static void setFeatureEnabled(ItemStack armor, String featureId, boolean enabled, boolean defaultEnabled) {
        setSubmoduleEnabled(armor, featureId, enabled, defaultEnabled);
    }

    public static int buildFeatureMask(ItemStack armor, List<OverloadArmorFeature> features) {
        int mask = 0;
        int limit = Math.min(features.size(), Integer.SIZE - 1);
        for (int index = 0; index < limit; index++) {
            if (isFeatureEnabled(armor, features.get(index))) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    public static int buildSubmoduleMask(ItemStack armor, List<OverloadArmorSubmodule> submodules) {
        int mask = 0;
        int limit = Math.min(submodules.size(), Integer.SIZE - 1);
        for (int index = 0; index < limit; index++) {
            if (isSubmoduleEnabled(armor, submodules.get(index))) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    public static TerminalSession beginTerminalSession(ItemStack armor) {
        long nextSessionVersion = getTerminalSessionVersion(armor) + 1L;
        setLongMetadata(armor, TAG_TERMINAL_SESSION_VERSION, nextSessionVersion);
        return new TerminalSession(nextSessionVersion, getTerminalContentVersion(armor));
    }

    public static long getTerminalContentVersion(ItemStack armor) {
        return getLongMetadata(armor, TAG_TERMINAL_CONTENT_VERSION);
    }

    public static long getTerminalSessionVersion(ItemStack armor) {
        return getLongMetadata(armor, TAG_TERMINAL_SESSION_VERSION);
    }

    public static boolean matchesTerminalSession(ItemStack armor, long sessionVersion, long contentVersion) {
        return getTerminalSessionVersion(armor) == sessionVersion
                && getTerminalContentVersion(armor) == contentVersion;
    }

    public static boolean writeTerminalForSession(
            ItemStack armor,
            HolderLookup.Provider registries,
            ItemStack terminal,
            long sessionVersion,
            long contentVersion
    ) {
        if (!matchesTerminalSession(armor, sessionVersion, contentVersion)) {
            return false;
        }

        setSlot(armor, registries, SLOT_TERMINAL, terminal);
        return true;
    }

    public static Snapshot snapshot(ItemStack armor, HolderLookup.Provider registries, boolean equipped) {
        return snapshot(null, armor, registries, equipped, Dist.CLIENT);
    }

    public static Snapshot snapshot(@Nullable Player player, ItemStack armor, HolderLookup.Provider registries, boolean equipped) {
        return snapshot(player, armor, registries, equipped, resolveDist(player != null && player.level().isClientSide()));
    }

    private static Snapshot snapshot(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            boolean equipped,
            Dist dist
    ) {
        var core = getSlot(armor, registries, SLOT_CORE);
        var buffer = getSlot(armor, registries, SLOT_BUFFER);
        var terminal = getSlot(armor, registries, SLOT_TERMINAL);
        var runtime = getRuntime(armor);
        var submodules = collectSubmodules(armor, registries);

        int baseOverload = core.is(ModItems.ULTIMATE_OVERLOAD_CORE.get()) ? ULTIMATE_CORE_OVERLOAD : 0;
        long bufferCapacity = buffer.getItem() instanceof LightningStorageComponentItem storage
                ? storage.getUsableCapacity()
                : 0L;

        int idleLoad = equipped ? EQUIPPED_IDLE_LOAD : 0;
        int bufferLoad = !buffer.isEmpty() ? BUFFER_IDLE_LOAD : 0;
        int submoduleIdleLoad = 0;
        int submoduleDynamicLoad = 0;
        // Install-time cap guarantees that every enabled module's idle load fits under baseOverload;
        // at runtime we just sum the contributions of enabled modules. Dynamic load can still push
        // the armor into deficit, which translates to energy draw / lockout downstream.
        for (var submodule : submodules) {
            if (!isSubmoduleEnabled(armor, submodule)) {
                continue;
            }
            int moduleIdleLoad = Math.max(submodule.getIdleOverloaded(player, dist, armor), 0);
            int moduleDynamicLoad = equipped ? getStoredSubmoduleDynamicLoad(armor, submodule.id()) : 0;
            submoduleIdleLoad += moduleIdleLoad;
            submoduleDynamicLoad += moduleDynamicLoad;
        }
        int currentLoad = idleLoad + bufferLoad + submoduleIdleLoad + submoduleDynamicLoad;
        // Terminal proxy is a built-in armor capability now: any ItemWT in the terminal slot
        // opens the terminal. We no longer attribute any load to it beyond the core idle load.
        boolean terminalProxyEnabled = terminal.getItem() instanceof ItemWT;
        int terminalLoad = 0;

        return new Snapshot(
                equipped,
                !core.isEmpty(),
                !buffer.isEmpty(),
                terminal.getItem() instanceof ItemWT,
                terminalProxyEnabled,
                runtime.storedEnergy(),
                runtime.unpaidEnergy(),
                runtime.debtTicks(),
                runtime.lockedTicks(),
                baseOverload,
                idleLoad,
                bufferLoad,
                terminalLoad,
                currentLoad,
                bufferCapacity);
    }

    /**
     * Session-local active-state cache, one map per logical side. We avoid relying on stack NBT for
     * edge detection because both Curios and vanilla equipment sync can hand us fresh ItemStack
     * copies whose mutations never make it back to the authoritative slot — stored-in-NBT flags
     * then churn and we re-fire lifecycle events. Keying by (armorId, submoduleId) survives those
     * copies for the lifetime of the JVM / logical side.
     */
    private static final java.util.Map<String, Boolean> CLIENT_ACTIVE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> SERVER_ACTIVE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static String cacheKey(UUID armorId, String submoduleId) {
        return armorId + "#" + submoduleId;
    }

    /**
     * Server-authoritative edge detection for submodule lifecycle. Runs the activate/deactivate
     * pass on the server, then broadcasts each edge to the owning client via
     * {@link com.moakiee.ae2lt.network.SubmoduleLifecyclePacket} so the client side fires the
     * mirroring hook in lock-step with the server. The client never calls this method; its cache
     * is updated exclusively through the lifecycle packet.
     */
    public static void syncSubmoduleActiveState(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            boolean equipped,
            Dist dist
    ) {
        if (dist != Dist.DEDICATED_SERVER) {
            return;
        }
        UUID armorId = ensureArmorId(armor);
        var cache = SERVER_ACTIVE_CACHE;

        for (var submodule : collectSubmodules(armor, registries)) {
            boolean active = canSubmoduleBeActive(armor, submodule, registries, equipped);
            String key = cacheKey(armorId, submodule.id());
            Boolean previous = cache.get(key);

            if (previous == null) {
                // Seed the session cache from the persisted active flag so post-login / post-load
                // transitions still fire correctly. If the armor was saved as active but is now
                // idle/unworn, we owe the module exactly one onDeactivated + saveData; if it was
                // saved inactive and is now active (genuine first equip), we fire onActivated.
                boolean storedActive = getSubmoduleRuntime(armor, submodule.id()).active();
                previous = storedActive;
                cache.put(key, storedActive);
            }

            if (previous == active) {
                continue;
            }

            cache.put(key, active);
            if (active) {
                getSubmoduleData(armor, submodule);
                submodule.onActivated(player, dist, armor);
            } else {
                submodule.onDeactivated(player, dist, armor);
                var raw = getStoredSubmoduleData(armor, submodule.id());
                var saved = submodule.saveData(armor, raw.copy());
                if (!saved.equals(raw)) {
                    setStoredSubmoduleData(armor, submodule.id(), saved);
                }
            }
            setSubmoduleRuntimeActive(armor, submodule.id(), active);

            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.moakiee.ae2lt.network.SubmoduleLifecyclePacket.broadcast(
                        serverPlayer, armorId, submodule.id(), active);
            }
        }
    }

    /**
     * Called by the client lifecycle packet handler to update the client-side cache and guarantee
     * the client never double-fires a hook the server has already instructed it to run.
     */
    public static void markClientActive(UUID armorId, String submoduleId, boolean active) {
        if (armorId == null || submoduleId == null || submoduleId.isBlank()) {
            return;
        }
        CLIENT_ACTIVE_CACHE.put(cacheKey(armorId, submoduleId), active);
    }

    public static boolean isClientSubmoduleActive(UUID armorId, String submoduleId) {
        if (armorId == null || submoduleId == null || submoduleId.isBlank()) {
            return false;
        }
        var value = CLIENT_ACTIVE_CACHE.get(cacheKey(armorId, submoduleId));
        return value != null && value;
    }

    /**
     * Updates the in-memory per-submodule runtime (active flag + dynamic load). Does not touch
     * NBT; those values are flushed with {@link #flushRuntimeToNbt(ItemStack)} at explicit save
     * points so the armor's CUSTOM_DATA stays stable tick-to-tick.
     */
    private static void setSubmoduleRuntimeActive(ItemStack armor, String submoduleId, boolean active) {
        UUID armorId = ensureArmorId(armor);
        String key = cacheKey(armorId, submoduleId);
        var previous = SUBMODULE_RUNTIME_CACHE.get(key);
        int dynamicLoad = !active ? 0 : (previous != null ? previous.dynamicLoad() : 0);
        SUBMODULE_RUNTIME_CACHE.put(key, new SubmoduleRuntime(active, dynamicLoad));
    }

    private static SubmoduleRuntime getSubmoduleRuntime(ItemStack armor, String submoduleId) {
        UUID armorId = getArmorId(armor);
        if (armorId != null) {
            var cached = SUBMODULE_RUNTIME_CACHE.get(cacheKey(armorId, submoduleId));
            if (cached != null) {
                return cached;
            }
        }
        var nbt = getStoredSubmoduleRuntime(armor, submoduleId);
        var loaded = new SubmoduleRuntime(readActiveState(nbt), getStoredDynamicLoad(nbt));
        if (armorId != null) {
            SUBMODULE_RUNTIME_CACHE.put(cacheKey(armorId, submoduleId), loaded);
        }
        return loaded;
    }

    private static void setSubmoduleRuntimeDynamicLoad(ItemStack armor, String submoduleId, int dynamicLoad) {
        UUID armorId = ensureArmorId(armor);
        String key = cacheKey(armorId, submoduleId);
        var previous = SUBMODULE_RUNTIME_CACHE.get(key);
        boolean active = previous != null && previous.active();
        SUBMODULE_RUNTIME_CACHE.put(key, new SubmoduleRuntime(active, Math.max(dynamicLoad, 0)));
    }

    /**
     * Clears the session cache for armor that has left the world (carrier dropped it, player
     * disconnected, etc.). Reserved for future GC — currently we leave stale entries, they're
     * cheap and re-converge on the next tick.
     */
    public static void forgetSubmoduleActiveCache(UUID armorId) {
        if (armorId == null) {
            return;
        }
        String prefix = armorId + "#";
        CLIENT_ACTIVE_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        SERVER_ACTIVE_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        SUBMODULE_RUNTIME_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void tickActiveSubmodules(
            @Nullable Player player,
            ItemStack armor,
            HolderLookup.Provider registries,
            Dist dist
    ) {
        for (var submodule : collectSubmodules(armor, registries)) {
            var runtime = getSubmoduleRuntime(armor, submodule.id());
            if (!runtime.active()) {
                continue;
            }
            int dynamicLoad = Math.max(submodule.tickActive(player, dist, armor), 0);
            if (dynamicLoad != runtime.dynamicLoad()) {
                setSubmoduleRuntimeDynamicLoad(armor, submodule.id(), dynamicLoad);
            }
        }
    }

    public static Snapshot tickEquipped(Player player, ItemStack armor, HolderLookup.Provider registries) {
        var snapshot = snapshot(player, armor, registries, true);
        var runtime = new RuntimeState(
                Math.min(snapshot.storedEnergy(), snapshot.bufferCapacity()),
                snapshot.unpaidEnergy(),
                snapshot.debtTicks(),
                snapshot.lockedTicks());

        if (!snapshot.hasCore() || !snapshot.hasBuffer() || snapshot.bufferCapacity() <= 0) {
            setRuntime(armor, new RuntimeState(0, 0, 0, 0));
            return snapshot(player, armor, registries, true);
        }

        if (runtime.lockedTicks() > 0) {
            setRuntime(armor, new RuntimeState(
                    Math.min(runtime.storedEnergy(), snapshot.bufferCapacity()),
                    0,
                    0,
                    runtime.lockedTicks() - 1));
            return snapshot(player, armor, registries, true);
        }

        long availableEnergy = Math.min(snapshot.bufferCapacity(), runtime.storedEnergy() + snapshot.powerGeneration());
        long totalDemand = runtime.unpaidEnergy() + snapshot.powerDemand();

        if (availableEnergy >= totalDemand) {
            setRuntime(armor, new RuntimeState(
                    availableEnergy - totalDemand,
                    0,
                    0,
                    0));
            return snapshot(player, armor, registries, true);
        }

        int nextDebtTicks = runtime.debtTicks() + 1;
        if (nextDebtTicks >= LOCK_TRIGGER_TICKS) {
            setRuntime(armor, new RuntimeState(0, 0, 0, LOCK_DURATION_TICKS));
            return snapshot(player, armor, registries, true);
        }

        setRuntime(armor, new RuntimeState(
                0,
                totalDemand - availableEnergy,
                nextDebtTicks,
                0));
        return snapshot(player, armor, registries, true);
    }

    private static CompoundTag getStoredSubmoduleData(ItemStack armor, String submoduleId) {
        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_SUBMODULE_DATA, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        var submoduleData = armorTag.getCompound(TAG_SUBMODULE_DATA);
        if (!submoduleData.contains(submoduleId, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return submoduleData.getCompound(submoduleId).copy();
    }

    private static void setStoredSubmoduleData(ItemStack armor, String submoduleId, CompoundTag data) {
        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();
            var submoduleData = armorTag.contains(TAG_SUBMODULE_DATA, CompoundTag.TAG_COMPOUND)
                    ? armorTag.getCompound(TAG_SUBMODULE_DATA)
                    : new CompoundTag();

            if (data == null || data.isEmpty()) {
                submoduleData.remove(submoduleId);
            } else {
                submoduleData.put(submoduleId, data.copy());
            }

            if (submoduleData.isEmpty()) {
                armorTag.remove(TAG_SUBMODULE_DATA);
            } else {
                armorTag.put(TAG_SUBMODULE_DATA, submoduleData);
            }
            rootTag.put(TAG_ROOT, armorTag);
        });
    }

    private static CompoundTag getStoredSubmoduleRuntime(ItemStack armor, String submoduleId) {
        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        var armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_SUBMODULE_RUNTIME, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        var runtimeData = armorTag.getCompound(TAG_SUBMODULE_RUNTIME);
        if (!runtimeData.contains(submoduleId, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return runtimeData.getCompound(submoduleId).copy();
    }

    private static void setStoredSubmoduleRuntime(ItemStack armor, String submoduleId, CompoundTag runtime) {
        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();
            var runtimeData = armorTag.contains(TAG_SUBMODULE_RUNTIME, CompoundTag.TAG_COMPOUND)
                    ? armorTag.getCompound(TAG_SUBMODULE_RUNTIME)
                    : new CompoundTag();

            if (runtime == null || runtime.isEmpty()) {
                runtimeData.remove(submoduleId);
            } else {
                runtimeData.put(submoduleId, runtime.copy());
            }

            if (runtimeData.isEmpty()) {
                armorTag.remove(TAG_SUBMODULE_RUNTIME);
            } else {
                armorTag.put(TAG_SUBMODULE_RUNTIME, runtimeData);
            }
            rootTag.put(TAG_ROOT, armorTag);
        });
    }

    private static boolean readActiveState(CompoundTag runtimeState) {
        return runtimeState.contains(TAG_RUNTIME_ACTIVE, CompoundTag.TAG_BYTE)
                && runtimeState.getBoolean(TAG_RUNTIME_ACTIVE);
    }

    private static int getStoredDynamicLoad(CompoundTag runtimeState) {
        return runtimeState.contains(TAG_RUNTIME_DYNAMIC_LOAD, CompoundTag.TAG_INT)
                ? Math.max(runtimeState.getInt(TAG_RUNTIME_DYNAMIC_LOAD), 0)
                : 0;
    }

    private static int getStoredSubmoduleDynamicLoad(ItemStack armor, String submoduleId) {
        return getSubmoduleRuntime(armor, submoduleId).dynamicLoad();
    }

    /**
     * "Active" is edge-triggered by equipment/install changes. Transient runtime conditions like
     * power starvation or overload lockout intentionally do NOT toggle active state — the module
     * stays active while its physical prerequisites remain (worn + core + buffer + enabled) and
     * can guard its own effects via {@link #isSubmodulePowered} or the snapshot inside
     * {@link OverloadArmorSubmodule#tickActive}.
     */
    private static boolean canSubmoduleBeActive(
            ItemStack armor,
            OverloadArmorSubmodule submodule,
            HolderLookup.Provider registries,
            boolean equipped
    ) {
        if (!equipped || !isSubmoduleEnabled(armor, submodule)) {
            return false;
        }

        var core = getSlot(armor, registries, SLOT_CORE);
        var buffer = getSlot(armor, registries, SLOT_BUFFER);
        long bufferCapacity = buffer.getItem() instanceof LightningStorageComponentItem storage
                ? storage.getUsableCapacity()
                : 0L;
        // Install-time cap enforces base overload limits at workbench insertion, so every installed
        // module already fits under baseOverload. Runtime simply gates on physical prerequisites
        // (worn + core + buffer) and the user-facing enabled toggle; overflow from core swaps is
        // absorbed naturally through power draw / lockout rather than individual deactivation.
        return !core.isEmpty() && !buffer.isEmpty() && bufferCapacity > 0;
    }

    /**
     * Runtime guard modules can use inside {@link OverloadArmorSubmodule#tickActive} to decide
     * whether to apply an effect this tick. Decoupled from the lifecycle (active) state so power
     * fluctuations don't cause onActivated/onDeactivated churn.
     */
    public static boolean isSubmodulePowered(ItemStack armor) {
        return getRuntime(armor).lockedTicks() <= 0;
    }

    private static CompoundTag readRootTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /**
     * Session-local runtime cache. We deliberately do NOT persist per-tick energy/debt churn into
     * the stack's CUSTOM_DATA: doing so makes every tick a "component change" from vanilla's
     * point of view, which causes LivingEquipmentChangeEvent spam and spurious equip-sound
     * replays whenever any UI path syncs the slot. Runtime is therefore cached in memory and
     * only flushed back to NBT on explicit persistence points (see {@link #flushRuntimeToNbt}).
     */
    private static final java.util.Map<UUID, RuntimeState> RUNTIME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Session-local cache of per-submodule runtime (active flag + dynamic overload load). Like
     * {@link #RUNTIME_CACHE}, written each tick during activation / tickActive processing and
     * flushed back to NBT only at explicit save points.
     */
    private static final java.util.Map<String, SubmoduleRuntime> SUBMODULE_RUNTIME_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record SubmoduleRuntime(boolean active, int dynamicLoad) {
    }

    private static RuntimeState readRuntimeFromNbt(ItemStack armor) {
        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return new RuntimeState(0, 0, 0, 0);
        }

        var armorTag = root.getCompound(TAG_ROOT);
        return new RuntimeState(
                Math.max(armorTag.getLong(TAG_STORED_ENERGY), 0),
                Math.max(armorTag.getLong(TAG_UNPAID_ENERGY), 0),
                Math.max(armorTag.getInt(TAG_DEBT_TICKS), 0),
                Math.max(armorTag.getInt(TAG_LOCKED_TICKS), 0));
    }

    private static RuntimeState getRuntime(ItemStack armor) {
        UUID id = getArmorId(armor);
        if (id != null) {
            var cached = RUNTIME_CACHE.get(id);
            if (cached != null) {
                return cached;
            }
        }
        var loaded = readRuntimeFromNbt(armor);
        if (id != null) {
            RUNTIME_CACHE.put(id, loaded);
        }
        return loaded;
    }

    private static void setRuntime(ItemStack armor, RuntimeState runtime) {
        UUID id = ensureArmorId(armor);
        RUNTIME_CACHE.put(id, runtime);
    }

    /**
     * Flush the in-memory runtime state into the stack's persistent NBT. Call on explicit save
     * points: armor unequip, level save hook, menu close. NBT writes during normal ticking are
     * skipped to avoid component churn.
     */
    public static void flushRuntimeToNbt(ItemStack armor) {
        UUID id = getArmorId(armor);
        if (id == null) {
            return;
        }
        var cached = RUNTIME_CACHE.get(id);
        if (cached != null) {
            writeRuntimeToNbt(armor, cached);
        }
        flushSubmoduleRuntimeToNbt(armor, id);
    }

    /**
     * Writes every cached SubmoduleRuntime that belongs to {@code armorId} back to the armor's
     * NBT. Invoked from {@link #flushRuntimeToNbt} so all runtime state shares the same persist-
     * ence points.
     */
    private static void flushSubmoduleRuntimeToNbt(ItemStack armor, UUID armorId) {
        String prefix = armorId + "#";
        for (var entry : SUBMODULE_RUNTIME_CACHE.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            String submoduleId = entry.getKey().substring(prefix.length());
            if (submoduleId.isBlank()) {
                continue;
            }
            var runtime = entry.getValue();
            var tag = new CompoundTag();
            tag.putBoolean(TAG_RUNTIME_ACTIVE, runtime.active());
            tag.putInt(TAG_RUNTIME_DYNAMIC_LOAD, Math.max(runtime.dynamicLoad(), 0));
            setStoredSubmoduleRuntime(armor, submoduleId, tag);
        }
    }

    private static void writeRuntimeToNbt(ItemStack armor, RuntimeState runtime) {
        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();

            armorTag.putLong(TAG_STORED_ENERGY, Math.max(runtime.storedEnergy(), 0));
            armorTag.putLong(TAG_UNPAID_ENERGY, Math.max(runtime.unpaidEnergy(), 0));
            armorTag.putInt(TAG_DEBT_TICKS, Math.max(runtime.debtTicks(), 0));
            armorTag.putInt(TAG_LOCKED_TICKS, Math.max(runtime.lockedTicks(), 0));
            rootTag.put(TAG_ROOT, armorTag);
        });
    }

    private static long getLongMetadata(ItemStack armor, String key) {
        var root = readRootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return 0L;
        }

        return Math.max(root.getCompound(TAG_ROOT).getLong(key), 0L);
    }

    private static void setLongMetadata(ItemStack armor, String key, long value) {
        CustomData.update(DataComponents.CUSTOM_DATA, armor, rootTag -> {
            var armorTag = rootTag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                    ? rootTag.getCompound(TAG_ROOT)
                    : new CompoundTag();
            armorTag.putLong(key, Math.max(value, 0L));
            rootTag.put(TAG_ROOT, armorTag);
        });
    }

    private static void incrementTerminalContentVersion(ItemStack armor) {
        setLongMetadata(armor, TAG_TERMINAL_CONTENT_VERSION, getTerminalContentVersion(armor) + 1L);
    }

    private static Dist resolveDist(boolean clientSide) {
        return clientSide ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }

    private record RuntimeState(long storedEnergy, long unpaidEnergy, int debtTicks, int lockedTicks) {
    }

    public record TerminalSession(long sessionVersion, long contentVersion) {
    }

    public record Snapshot(
            boolean equipped,
            boolean hasCore,
            boolean hasBuffer,
            boolean hasTerminal,
            boolean terminalProxyEnabled,
            long storedEnergy,
            long unpaidEnergy,
            int debtTicks,
            int lockedTicks,
            int baseOverload,
            int idleLoad,
            int bufferLoad,
            int terminalLoad,
            int currentLoad,
            long bufferCapacity
    ) {
        public int remainingLoad() {
            return baseOverload - currentLoad;
        }

        public boolean overloaded() {
            return currentLoad > baseOverload;
        }

        public long powerGeneration() {
            if (remainingLoad() <= 0) {
                return 0;
            }
            return (long) remainingLoad() * remainingLoad();
        }

        public long powerDemand() {
            if (remainingLoad() >= 0) {
                return 0;
            }
            long overload = Math.abs((long) remainingLoad());
            return overload * overload;
        }

        public boolean locked() {
            return lockedTicks > 0;
        }

        public boolean canOpenTerminal() {
            return equipped
                    && hasCore
                    && hasBuffer
                    && hasTerminal
                    && terminalProxyEnabled
                    && !overloaded()
                    && !locked();
        }
    }
}
