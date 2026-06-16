package com.moakiee.ae2lt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class TestBatchCraftingCoreIntegrationContractTest {
    @Test
    void registersInWorldGridNodeCapability() throws IOException {
        String source = readSource("src/main/java/com/moakiee/ae2lt/AE2LightningTech.java");
        var registration = Pattern.compile(
                "event\\.registerBlockEntity\\(\\s*"
                        + "AECapabilities\\.IN_WORLD_GRID_NODE_HOST,\\s*"
                        + "ModBlockEntities\\.TEST_BATCH_CRAFTING_CORE\\.get\\(\\)",
                Pattern.DOTALL);

        assertTrue(registration.matcher(source).find(),
                "test batch crafting core must expose IN_WORLD_GRID_NODE_HOST so adjacent AE2 nodes can connect");
    }

    @Test
    void keepsPatternProviderChannelFlag() throws IOException {
        String source = readSource(
                "src/main/java/com/moakiee/ae2lt/logic/craft/TestBatchCraftingCoreLogic.java");

        assertFalse(Pattern.compile("\\bmainNode\\.setFlags\\s*\\(\\s*\\)").matcher(source).find(),
                "empty setFlags() clears PatternProviderLogic's REQUIRE_CHANNEL flag");
    }

    @Test
    void usesTwoMillionParallelTestThreads() throws IOException {
        String source = readSource(
                "src/main/java/com/moakiee/ae2lt/logic/craft/TestBatchCraftingCoreLogic.java");

        assertTrue(source.contains("TEST_MAX_THREADS = 2_000_000"),
                "test batch crafting core should allow 2,000,000 parallel copies");
    }

    private static String readSource(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
