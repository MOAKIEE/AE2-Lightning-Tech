package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ArmorNetworkRechargePolicyTest {

    @Test
    void passiveRechargeOnlyStartsBelowHalfCapacityAndRequestsFullRoom() {
        assertFalse(ArmorNetworkRechargePolicy.shouldPassiveRecharge(500L, 1_000L));
        assertFalse(ArmorNetworkRechargePolicy.shouldPassiveRecharge(501L, 1_000L));
        assertTrue(ArmorNetworkRechargePolicy.shouldPassiveRecharge(499L, 1_000L));
        assertEquals(501L, ArmorNetworkRechargePolicy.passiveRechargeRequest(499L, 1_000L));
    }

    @Test
    void activeRechargeOnlyStartsWhenLocalBufferCannotPayAndRequestsFullRoom() {
        assertFalse(ArmorNetworkRechargePolicy.shouldActiveRecharge(1_000L, 1_000L, 500L));
        assertFalse(ArmorNetworkRechargePolicy.shouldActiveRecharge(500L, 1_000L, 500L));
        assertTrue(ArmorNetworkRechargePolicy.shouldActiveRecharge(499L, 1_000L, 500L));
        assertEquals(501L, ArmorNetworkRechargePolicy.activeRechargeRequest(499L, 1_000L, 500L));
    }

    @Test
    void failedRechargeSkipsRetryUntilCooldownExpires() {
        long nextRetryTick = ArmorNetworkRechargePolicy.nextRetryTick(20L);

        assertEquals(40L, nextRetryTick);
        assertTrue(ArmorNetworkRechargePolicy.isCoolingDown(nextRetryTick, 21L));
        assertTrue(ArmorNetworkRechargePolicy.isCoolingDown(nextRetryTick, 39L));
        assertFalse(ArmorNetworkRechargePolicy.isCoolingDown(nextRetryTick, 40L));
    }
}
