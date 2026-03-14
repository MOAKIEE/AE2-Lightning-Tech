package com.moakiee.ae2lt.overload.cpu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEItemKey;

import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;

/**
 * Per-CPU overload-side waiting state.
 * <p>
 * Recommended structure:
 * <ul>
 *   <li>a primary map keyed by (craftingId, patternIdentity, outputSlotIndex)</li>
 *   <li>a secondary index by item id for fast ID_ONLY claim lookup</li>
 *   <li>stable registration order so repeated claims are deterministic</li>
 * </ul>
 * This leaves AE2's native {@code waitingFor} untouched and tracks only the
 * extra semantics needed for overload ID_ONLY outputs.
 */
public final class OverloadCpuState {
    private final OverloadCpuOwner owner;
    private final Map<PendingOverloadOutputKey, PendingOverloadOutput> pendingByKey = new LinkedHashMap<>();
    private final Map<ResourceLocation, LinkedHashSet<PendingOverloadOutputKey>> pendingByItemId = new LinkedHashMap<>();
    private long nextSequence = 1L;

    public OverloadCpuState(OverloadCpuOwner owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public OverloadCpuOwner owner() {
        return owner;
    }

    public Collection<PendingOverloadOutput> allPending() {
        return List.copyOf(pendingByKey.values());
    }

    public boolean isEmpty() {
        return pendingByKey.isEmpty();
    }

    public void registerExpectedOutputs(OverloadPatternReference patternReference,
                                        OverloadPatternDetails patternDetails,
                                        long pushedCopies) {
        Objects.requireNonNull(patternReference, "patternReference");
        Objects.requireNonNull(patternDetails, "patternDetails");
        if (pushedCopies <= 0) {
            throw new IllegalArgumentException("pushedCopies must be > 0");
        }

        for (var output : patternDetails.outputs()) {
            if (output.matchMode() != MatchMode.ID_ONLY) {
                continue;
            }

            var itemId = itemIdOf(output);
            var amount = output.amountPerCraft() * pushedCopies;
            var key = new PendingOverloadOutputKey(owner.craftingId(), patternReference.patternIdentity(),
                    output.slotIndex());
            var existing = pendingByKey.get(key);
            if (existing != null) {
                existing.addExpected(amount);
                continue;
            }

            var pending = new PendingOverloadOutput(
                    key,
                    owner,
                    patternReference,
                    itemId,
                    amount,
                    output.primaryOutput(),
                    nextSequence++);
            pendingByKey.put(key, pending);
            pendingByItemId.computeIfAbsent(itemId, ignored -> new LinkedHashSet<>()).add(key);
        }
    }

    public OverloadClaimResult claimByItemId(ResourceLocation itemId, long amount, boolean mutate,
                                             boolean primaryOutput) {
        Objects.requireNonNull(itemId, "itemId");
        if (amount <= 0) {
            return OverloadClaimResult.EMPTY;
        }

        var keys = pendingByItemId.get(itemId);
        if (keys == null || keys.isEmpty()) {
            return OverloadClaimResult.EMPTY;
        }

        long remaining = amount;
        var claims = new ArrayList<PendingOverloadClaim>();

        var ordered = keys.stream()
                .map(pendingByKey::get)
                .filter(Objects::nonNull)
                .filter(pending -> pending.primaryOutput() == primaryOutput)
                .sorted(Comparator.comparingLong(PendingOverloadOutput::registeredOrder))
                .toList();

        for (var pending : ordered) {
            if (remaining <= 0) {
                break;
            }

            long claimable = Math.min(pending.remainingAmount(), remaining);
            if (claimable <= 0) {
                continue;
            }

            if (mutate) {
                pending.claim(claimable);
                if (pending.isSatisfied()) {
                    removeSatisfied(pending);
                }
            }

            claims.add(new PendingOverloadClaim(pending.key(), claimable));
            remaining -= claimable;
        }

        long claimedAmount = amount - remaining;
        return claimedAmount > 0 ? new OverloadClaimResult(claimedAmount, claims) : OverloadClaimResult.EMPTY;
    }

    public long getRemainingForItem(ResourceLocation itemId) {
        Objects.requireNonNull(itemId, "itemId");
        var keys = pendingByItemId.get(itemId);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        long total = 0;
        for (var key : keys) {
            var pending = pendingByKey.get(key);
            if (pending != null) {
                total += pending.remainingAmount();
            }
        }
        return total;
    }

    public void clear() {
        pendingByKey.clear();
        pendingByItemId.clear();
    }

    private void removeSatisfied(PendingOverloadOutput pending) {
        pendingByKey.remove(pending.key());
        var keys = pendingByItemId.get(pending.itemId());
        if (keys != null) {
            keys.remove(pending.key());
            if (keys.isEmpty()) {
                pendingByItemId.remove(pending.itemId());
            }
        }
    }

    private static ResourceLocation itemIdOf(OverloadPatternDetails.OutputSlot output) {
        var key = AEItemKey.of(output.template());
        if (key == null) {
            throw new IllegalArgumentException("output template must resolve to an item key");
        }
        return key.getId();
    }
}
