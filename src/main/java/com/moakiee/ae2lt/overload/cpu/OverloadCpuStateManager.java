package com.moakiee.ae2lt.overload.cpu;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

import net.minecraft.resources.ResourceLocation;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;

import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;

/**
 * Runtime registry for overload-side CPU waiting state.
 * <p>
 * Lifecycle skeleton:
 * <ul>
 *   <li>create/get when an overload pattern copy is pushed successfully</li>
 *   <li>register only outputs whose match mode is ID_ONLY</li>
 *   <li>decrement during CPU insert-path matching when an incoming item can be
 *       recognized by item id but not by AE2's exact waitingFor key</li>
 *   <li>remove entries when remaining amount reaches zero</li>
 *   <li>clear the entire CPU state on finish, cancel, or job replacement</li>
 * </ul>
 * This manager intentionally does not change AE2's native waitingFor structure.
 */
public final class OverloadCpuStateManager {
    public static final OverloadCpuStateManager INSTANCE = new OverloadCpuStateManager();

    private final Map<CraftingCpuLogic, OverloadCpuState> states = new WeakHashMap<>();

    private OverloadCpuStateManager() {
    }

    public synchronized OverloadCpuState getOrCreate(CraftingCpuLogic logic) {
        Objects.requireNonNull(logic, "logic");
        return states.computeIfAbsent(logic, ignored -> new OverloadCpuState(OverloadCpuOwner.from(logic)));
    }

    public synchronized Optional<OverloadCpuState> get(CraftingCpuLogic logic) {
        Objects.requireNonNull(logic, "logic");
        return Optional.ofNullable(states.get(logic));
    }

    /**
     * Register the extra ID_ONLY outputs for one successfully pushed overload
     * pattern copy.
     */
    public synchronized void registerExpectedOutputs(CraftingCpuLogic logic,
                                                     OverloadPatternReference patternReference,
                                                     OverloadPatternDetails patternDetails,
                                                     long pushedCopies) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(patternReference, "patternReference");
        Objects.requireNonNull(patternDetails, "patternDetails");
        if (pushedCopies <= 0) {
            throw new IllegalArgumentException("pushedCopies must be > 0");
        }

        getOrCreate(logic).registerExpectedOutputs(patternReference, patternDetails, pushedCopies);
    }

    /**
     * Simulate or modulate a claim against overload ID_ONLY outputs.
     * <p>
     * The intended future use is as a fallback after AE2 exact waitingFor lookup
     * fails for an incoming item key.
     */
    public synchronized OverloadClaimResult claim(CraftingCpuLogic logic, AEKey incoming, long amount,
                                                  Actionable actionable) {
        return claim(logic, incoming, amount, actionable, false);
    }

    public synchronized OverloadClaimResult claimSecondary(CraftingCpuLogic logic, AEKey incoming, long amount,
                                                           Actionable actionable) {
        return claim(logic, incoming, amount, actionable, false);
    }

    public synchronized OverloadClaimResult claimPrimary(CraftingCpuLogic logic, AEKey incoming, long amount,
                                                         Actionable actionable) {
        return claim(logic, incoming, amount, actionable, true);
    }

    private synchronized OverloadClaimResult claim(CraftingCpuLogic logic, AEKey incoming, long amount,
                                                   Actionable actionable, boolean primaryOutput) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(actionable, "actionable");
        if (amount <= 0) {
            return OverloadClaimResult.EMPTY;
        }

        var itemKey = asItemKey(incoming);
        if (itemKey == null) {
            return OverloadClaimResult.EMPTY;
        }

        var state = states.get(logic);
        if (state == null) {
            return OverloadClaimResult.EMPTY;
        }

        var result = state.claimByItemId(itemKey.getId(), amount, actionable == Actionable.MODULATE, primaryOutput);
        if (actionable == Actionable.MODULATE && state.isEmpty()) {
            states.remove(logic);
        }
        return result;
    }

    public synchronized long getRemainingForItem(CraftingCpuLogic logic, ResourceLocation itemId) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(itemId, "itemId");
        var state = states.get(logic);
        return state != null ? state.getRemainingForItem(itemId) : 0;
    }

    public synchronized List<PendingOverloadOutput> snapshotPending(CraftingCpuLogic logic) {
        Objects.requireNonNull(logic, "logic");
        var state = states.get(logic);
        return state != null ? List.copyOf(state.allPending()) : List.of();
    }

    /**
     * Clear all overload-side state for this CPU.
     * Call from job cancel, normal finish, and any path that replaces the active
     * crafting job.
     */
    public synchronized void clear(CraftingCpuLogic logic) {
        Objects.requireNonNull(logic, "logic");
        states.remove(logic);
    }

    private static AEItemKey asItemKey(AEKey key) {
        return key instanceof AEItemKey itemKey ? itemKey : null;
    }
}
