package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorReachModuleContractTest {

    @Test
    void reachModuleIsRegisteredAndVisibleInCreativeTab() throws Exception {
        String items = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/registry/ModItems.java"));
        String main = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/AE2LightningTech.java"));

        assertTrue(items.contains("ARMOR_SUBMODULE_REACH_EXTENSION"));
        assertTrue(items.contains("\"module_reach_extension\""));
        assertTrue(main.contains("ARMOR_SUBMODULE_REACH_EXTENSION"));
    }

    @Test
    void reachModuleDeclaresConfigurableInteractionRangeCapability() throws Exception {
        String capability = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/device/capability/DeviceCapability.java"));
        String item = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/ReachSubmoduleItem.java"));
        String submodule = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/ReachSubmodule.java"));

        assertTrue(capability.contains("record InteractionRange"));
        assertTrue(item.contains("new DeviceCapability.InteractionRange"));
        assertTrue(item.contains("ArmorPart.CHEST"));
        assertTrue(submodule.contains("ReachDistanceOption.CONFIG_KEY"));
        assertTrue(submodule.contains("getConfigs"));
        assertTrue(submodule.contains("setConfig"));
    }

    @Test
    void reachServiceAppliesBothBlockAndEntityInteractionAttributes() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/service/ArmorInteractionRangeService.java"));

        assertTrue(service.contains("Attributes.BLOCK_INTERACTION_RANGE"));
        assertTrue(service.contains("Attributes.ENTITY_INTERACTION_RANGE"));
        assertTrue(service.contains("addOrUpdateTransientModifier"));
        assertTrue(service.contains("removeModifier"));
    }

    @Test
    void languageFilesNameReachModuleAndConfig() throws Exception {
        String english = Files.readString(Path.of("src/main/resources/assets/ae2lt/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        assertTrue(english.contains("ae2lt.overload_armor.feature.reach_extension.name"));
        assertTrue(english.contains("ae2lt.overload_armor.config.reach_range"));
        assertTrue(chinese.contains("ae2lt.overload_armor.feature.reach_extension.name"));
        assertTrue(chinese.contains("ae2lt.overload_armor.config.reach_range"));
    }
}
