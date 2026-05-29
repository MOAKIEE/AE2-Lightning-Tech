package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.overload.armor.state.ArmorRuntimeRegistry;

final class ArmorRuntimeRegistryTest {

    @Test
    void serverAndClientCachesStaySeparateAndClearDropsRuntimeState() {
        UUID armorId = UUID.randomUUID();
        ArmorRuntimeRegistry.clear(armorId);

        ArmorRuntimeRegistry.setServerSubmoduleActive(armorId, "flight", true);
        assertTrue(ArmorRuntimeRegistry.isServerSubmoduleActive(armorId, "flight"));
        assertFalse(ArmorRuntimeRegistry.isClientSubmoduleActive(armorId, "flight"));

        ArmorRuntimeRegistry.setClientSubmoduleActive(armorId, "flight", true);
        assertTrue(ArmorRuntimeRegistry.isClientSubmoduleActive(armorId, "flight"));
        assertTrue(ArmorRuntimeRegistry.isAnyClientSubmoduleActive("flight"));

        ArmorRuntimeRegistry.setSubmoduleRuntimeActive(armorId, "flight", true);
        assertTrue(ArmorRuntimeRegistry.isSubmoduleRuntimeActive(armorId, "flight"));

        ArmorRuntimeRegistry.clear(armorId);
        assertFalse(ArmorRuntimeRegistry.isServerSubmoduleActive(armorId, "flight"));
        assertFalse(ArmorRuntimeRegistry.isClientSubmoduleActive(armorId, "flight"));
        assertFalse(ArmorRuntimeRegistry.isSubmoduleRuntimeActive(armorId, "flight"));
    }
}
