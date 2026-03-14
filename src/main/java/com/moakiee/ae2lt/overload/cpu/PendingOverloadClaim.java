package com.moakiee.ae2lt.overload.cpu;

import java.util.Objects;

/**
 * One concrete claim against one pending overload output entry.
 */
public record PendingOverloadClaim(
        PendingOverloadOutputKey key,
        long claimedAmount
) {
    public PendingOverloadClaim {
        Objects.requireNonNull(key, "key");
        if (claimedAmount <= 0) {
            throw new IllegalArgumentException("claimedAmount must be > 0");
        }
    }
}
