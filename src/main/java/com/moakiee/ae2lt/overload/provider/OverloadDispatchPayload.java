package com.moakiee.ae2lt.overload.provider;

import java.util.List;
import java.util.Objects;

/**
 * Provider-owned payload assembled from exact extracted stacks.
 * <p>
 * The machine adapter should only see the final payload shape it needs to push,
 * not the logic that resolved id-only requirements against network inventory.
 */
public record OverloadDispatchPayload(
        int copies,
        List<PayloadInputSlot> inputSlots
) {
    public OverloadDispatchPayload {
        if (copies <= 0) {
            throw new IllegalArgumentException("copies must be > 0");
        }
        inputSlots = List.copyOf(Objects.requireNonNull(inputSlots, "inputSlots"));
    }
}
