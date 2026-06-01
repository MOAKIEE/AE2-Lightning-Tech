package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorCleanseModuleRulesTest {

    @Test
    void cleanseDefaultsToTwoSecondsAndRemovesAllHarmfulEffects() throws Exception {
        String configSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/config/AE2LTCommonConfig.java"));
        String itemSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/CleanseSubmoduleItem.java"));
        String compactItemSource = itemSource.replaceAll("\\s+", "");

        assertTrue(
                configSource.contains(".defineInRange(\"cleansePeriodTicks\", 40,"),
                "Cleanse should attempt to remove harmful effects every 40 ticks.");
        assertTrue(
                compactItemSource.contains("newDeviceCapability.CleanseTuning(AE2LTCommonConfig.overloadArmorCleansePeriodTicks(),Integer.MAX_VALUE)"),
                "Cleanse should remove all harmful effects in a cleanse attempt.");
        assertFalse(
                compactItemSource.contains("newDeviceCapability.CleanseTuning(AE2LTCommonConfig.overloadArmorCleansePeriodTicks(),1)"),
                "Cleanse should no longer be limited to one harmful effect per attempt.");
    }
}
