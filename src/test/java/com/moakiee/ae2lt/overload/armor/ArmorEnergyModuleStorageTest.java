package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorEnergyModuleStorageTest {

    @Test
    void nullRegistryCapacityFallsBackToCachedModuleCapacity() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/ArmorEnergyModuleStorage.java"));

        assertFalse(
                source.contains("if (registries == null) {\n            return 0L;\n        }")
                        || source.contains("if (registries == null) {\r\n            return 0L;\r\n        }"),
                "Inventory item bars call capacity without registry access, so armor energy modules need a cached capacity fallback.");
    }
}
