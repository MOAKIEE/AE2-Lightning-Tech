package com.moakiee.ae2lt.overload.cpu;

import java.util.List;
import java.util.Objects;

/**
 * Result of attempting to claim incoming items against overload-side pending
 * outputs.
 */
public record OverloadClaimResult(
        long claimedAmount,
        List<PendingOverloadClaim> claims
) {
    public static final OverloadClaimResult EMPTY = new OverloadClaimResult(0, List.of());

    public OverloadClaimResult {
        if (claimedAmount < 0) {
            throw new IllegalArgumentException("claimedAmount must be >= 0");
        }
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
    }

    public boolean claimedAnything() {
        return claimedAmount > 0;
    }
}
