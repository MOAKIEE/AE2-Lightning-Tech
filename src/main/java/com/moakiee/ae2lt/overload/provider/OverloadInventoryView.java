package com.moakiee.ae2lt.overload.provider;

import java.util.List;
import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import com.moakiee.ae2lt.overload.model.CompareKey;

/**
 * Provider-side view of the ME inventory used by overload input resolution.
 * <p>
 * This is intentionally kept separate from {@link com.moakiee.ae2lt.logic.MachineAdapter}.
 * The provider resolves and extracts ingredients from the network first; the
 * machine adapter only receives the resulting payload.
 */
public interface OverloadInventoryView {

    /**
     * Returns all exact inventory entries that could satisfy the requested
     * compare key. Each returned candidate still represents one exact storage
     * key, not an id-only aggregate.
     */
    List<OverloadInventoryCandidate> findCandidates(CompareKey requestedKey);

    /**
     * Extracts one exact inventory entry by its storage key.
     *
     * @param amount requested amount to extract from this exact key
     * @return the concrete extraction result; may be smaller than requested
     */
    ExtractedStack extract(AEKey storageKey, long amount, IActionSource source);

    record ExtractedStack(
            AEKey storageKey,
            ItemStack stack,
            long extractedAmount
    ) {
        public ExtractedStack {
            Objects.requireNonNull(storageKey, "storageKey");
            Objects.requireNonNull(stack, "stack");
            if (extractedAmount < 0) {
                throw new IllegalArgumentException("extractedAmount must be >= 0");
            }
        }

        public boolean isEmpty() {
            return extractedAmount <= 0 || stack.isEmpty();
        }
    }
}
