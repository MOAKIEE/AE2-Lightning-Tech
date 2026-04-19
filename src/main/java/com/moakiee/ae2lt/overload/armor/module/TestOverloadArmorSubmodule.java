package com.moakiee.ae2lt.overload.armor.module;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class TestOverloadArmorSubmodule extends AbstractOverloadArmorSubmodule {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(TestOverloadArmorSubmodule.class);
    private static final String KEY_BOOLEAN = "boolean_value";
    private static final String KEY_ENUM = "enum_value";
    private static final String KEY_NUMBER = "number_value";
    private static final String KEY_STRING = "string_value";
    private static final String KEY_LIST = "list_value";
    private static final String KEY_COMPOUND = "compound_value";

    @Override
    public String id() {
        return "test_module";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.test_module.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.test_module.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getIdleOverloaded(@Nullable Player player, Dist dist, ItemStack armor) {
        // Non-zero so the workbench's idle-budget cap is observable in-world: with the ULTIMATE
        // core's 128 budget this would theoretically allow 8 instances (128 / 16), but the
        // per-type cap below clamps it to 5 first — the test module thus exercises both codepaths.
        return 16;
    }

    @Override
    public int getMaxInstallAmount() {
        // Per-type install cap demo: the workbench hard-caps the test module at 5 regardless of
        // idle-budget headroom. Useful for validating the cap path without relying on the idle
        // sum hitting base-overload first.
        return 5;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        notifyLifecycle(player, dist, "activated");
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        notifyLifecycle(player, dist, "deactivated");
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player == null) {
            return 0;
        }
        return (int) Math.floorMod(player.level().getGameTime(), 5L);
    }

    @Override
    public CompoundTag loadData(ItemStack armor, CompoundTag data) {
        boolean seedsNeeded = needsSeeding(data);
        var normalized = data.copy();
        var options = normalized.contains(OPTIONS_TAG, CompoundTag.TAG_COMPOUND)
                ? normalized.getCompound(OPTIONS_TAG).copy()
                : new CompoundTag();

        if (!options.contains(KEY_BOOLEAN, Tag.TAG_BYTE)) {
            options.putBoolean(KEY_BOOLEAN, true);
        }
        if (!options.contains(KEY_ENUM, Tag.TAG_STRING)) {
            options.putString(KEY_ENUM, TestMode.ALPHA.name());
        }
        if (!options.contains(KEY_NUMBER, Tag.TAG_INT)) {
            options.putInt(KEY_NUMBER, 42);
        }
        if (!options.contains(KEY_STRING, Tag.TAG_STRING)) {
            options.putString(KEY_STRING, "hello_test_module");
        }
        if (!options.contains(KEY_LIST, Tag.TAG_LIST)) {
            options.put(KEY_LIST, createDefaultList());
        }
        if (!options.contains(KEY_COMPOUND, Tag.TAG_COMPOUND)) {
            options.put(KEY_COMPOUND, createDefaultCompound());
        }

        normalized.put(OPTIONS_TAG, options);
        // Only notify on meaningful seeding (first activation / corrupted data), so repeated
        // no-op loads from the framework don't spam the chat.
        if (seedsNeeded) {
            LOG.info("[ae2lt] test_module.loadData seeded defaults");
        }
        return normalized;
    }

    @Override
    public CompoundTag saveData(ItemStack armor, CompoundTag data) {
        return data;
    }

    private static boolean needsSeeding(CompoundTag data) {
        if (!data.contains(OPTIONS_TAG, CompoundTag.TAG_COMPOUND)) {
            return true;
        }
        var options = data.getCompound(OPTIONS_TAG);
        return !options.contains(KEY_BOOLEAN, Tag.TAG_BYTE)
                || !options.contains(KEY_ENUM, Tag.TAG_STRING)
                || !options.contains(KEY_NUMBER, Tag.TAG_INT)
                || !options.contains(KEY_STRING, Tag.TAG_STRING)
                || !options.contains(KEY_LIST, Tag.TAG_LIST)
                || !options.contains(KEY_COMPOUND, Tag.TAG_COMPOUND);
    }

    @Override
    public List<OverloadArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        var options = getOptions(armor);
        return List.of(
                config(
                        KEY_BOOLEAN,
                        Component.translatable("ae2lt.overload_armor.feature.test_module.config.boolean"),
                        getOptionOrDefault(options, KEY_BOOLEAN, ByteTag.valueOf(true)),
                        booleanChoices(),
                        Component.translatable("ae2lt.overload_armor.config.toggle_hint")),
                enumConfig(
                        KEY_ENUM,
                        Component.translatable("ae2lt.overload_armor.feature.test_module.config.enum"),
                        readMode(options),
                        TestMode.class,
                        mode -> Component.translatable(mode.translationKey())),
                config(
                        Component.translatable("ae2lt.overload_armor.feature.test_module.config.number"),
                        KEY_NUMBER,
                        getOptionOrDefault(options, KEY_NUMBER, IntTag.valueOf(42))),
                config(
                        Component.translatable("ae2lt.overload_armor.feature.test_module.config.string"),
                        KEY_STRING,
                        getOptionOrDefault(options, KEY_STRING, StringTag.valueOf("hello_test_module"))),
                config(
                        Component.translatable("ae2lt.overload_armor.feature.test_module.config.list"),
                        KEY_LIST,
                        getOptionOrDefault(options, KEY_LIST, createDefaultList())),
                config(
                        Component.translatable("ae2lt.overload_armor.feature.test_module.config.compound"),
                        KEY_COMPOUND,
                        getOptionOrDefault(options, KEY_COMPOUND, createDefaultCompound())));
    }

    private void notifyLifecycle(@Nullable Player player, Dist dist, String actionKey) {
        if (player == null) {
            return;
        }
        String sideKey = dist == Dist.CLIENT ? "client" : "server";
        player.displayClientMessage(Component.translatable(
                "ae2lt.overload_armor.feature.test_module.message." + actionKey + "." + sideKey), false);
    }

    private static Tag getOptionOrDefault(CompoundTag options, String key, Tag fallback) {
        return options.contains(key) ? options.get(key) : fallback;
    }

    private static ListTag createDefaultList() {
        var list = new ListTag();
        list.add(StringTag.valueOf("alpha"));
        list.add(StringTag.valueOf("beta"));
        return list;
    }

    private static CompoundTag createDefaultCompound() {
        var nested = new CompoundTag();
        nested.putString("note", "nested_payload");
        nested.putInt("charge", 7);
        return nested;
    }

    private static TestMode readMode(CompoundTag options) {
        if (!options.contains(KEY_ENUM, Tag.TAG_STRING)) {
            return TestMode.ALPHA;
        }

        var raw = options.getString(KEY_ENUM);
        try {
            return TestMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TestMode.ALPHA;
        }
    }

    private enum TestMode {
        ALPHA,
        BETA,
        GAMMA;

        private String translationKey() {
            return "ae2lt.overload_armor.feature.test_module.config.enum."
                    + name().toLowerCase(Locale.ROOT);
        }
    }
}
