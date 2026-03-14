package com.moakiee.ae2lt.overload.provider;

import java.util.List;
import java.util.Objects;

/**
 * Provider-side extraction plan for one pattern dispatch attempt.
 * <p>
 * The plan is intentionally exact-key based. Capacity forecasting remains out
 * of scope; if the target accepts only part of the payload, the remainder is
 * handled by the provider in later retries.
 */
public record ExtractionPlan(
        int copies,
        List<PlannedInputSlot> slots
) {
    public ExtractionPlan {
        if (copies <= 0) {
            throw new IllegalArgumentException("copies must be > 0");
        }
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
    }

    public boolean isComplete() {
        return slots.stream().allMatch(PlannedInputSlot::isSatisfied);
    }
}
