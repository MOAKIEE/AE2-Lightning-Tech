package com.moakiee.ae2lt.overload.armor.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public abstract class AbstractOverloadArmorSubmodule implements OverloadArmorSubmodule {
    protected static final String OPTIONS_TAG = "Options";

    protected CompoundTag getOptions(ItemStack armor) {
        var data = getData(armor);
        if (!data.contains(OPTIONS_TAG, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return data.getCompound(OPTIONS_TAG).copy();
    }

    protected void setOptions(ItemStack armor, CompoundTag options) {
        var data = getData(armor);
        if (options == null || options.isEmpty()) {
            data.remove(OPTIONS_TAG);
        } else {
            data.put(OPTIONS_TAG, options.copy());
        }
        setData(armor, data);
    }

    @Override
    public List<OverloadArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        var configs = new ArrayList<OverloadArmorSubmoduleConfig>();
        var options = getOptions(armor);
        options.getAllKeys().stream().sorted().forEach(key -> {
            var tag = options.get(key);
            if (tag != null) {
                configs.add(defaultConfig(key, tag));
            }
        });
        return List.copyOf(configs);
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (key == null || key.isBlank()) {
            return false;
        }

        boolean knownConfig = getConfigs(armor).stream().anyMatch(config -> config.key().equals(key));
        if (!knownConfig) {
            return false;
        }

        var options = getOptions(armor);
        if (value == null) {
            options.remove(key);
        } else {
            options.put(key, value.copy());
        }
        setOptions(armor, options);
        return true;
    }

    @Override
    public List<OverloadArmorSubmoduleOptionUi> getConfigUI(ItemStack armor) {
        var configUi = new ArrayList<OverloadArmorSubmoduleOptionUi>();
        for (var config : getConfigs(armor)) {
            var choice = config.currentChoice();
            OverloadArmorSubmoduleOptionUi.Kind kind;
            if (!config.editable()) {
                kind = OverloadArmorSubmoduleOptionUi.Kind.READ_ONLY;
            } else if (isBooleanChoiceSet(config.choices())) {
                kind = OverloadArmorSubmoduleOptionUi.Kind.BOOLEAN;
            } else {
                kind = OverloadArmorSubmoduleOptionUi.Kind.CYCLE;
            }
            configUi.add(new OverloadArmorSubmoduleOptionUi(
                    config.key(),
                    config.label(),
                    choice != null ? choice.label() : formatOptionValue(config.value()),
                    config.editable(),
                    config.hint() != null ? config.hint() : defaultHint(config),
                    kind));
        }
        return List.copyOf(configUi);
    }

    protected OverloadArmorSubmoduleConfig defaultConfig(String key, Tag value) {
        if (isBooleanOption(value)) {
            return config(
                    key,
                    Component.literal(formatOptionLabel(key)),
                    value,
                    booleanChoices(),
                    Component.translatable("ae2lt.overload_armor.config.toggle_hint"));
        }
        return config(Component.literal(formatOptionLabel(key)), key, value);
    }

    protected OverloadArmorSubmoduleConfig config(
            Component label,
            String key,
            Tag value
    ) {
        return config(key, label, value, List.of(), null);
    }

    protected OverloadArmorSubmoduleConfig config(
            String key,
            Component label,
            Tag value,
            List<OverloadArmorSubmoduleConfigChoice> choices,
            @Nullable Component hint
    ) {
        return new OverloadArmorSubmoduleConfig(key, label, value, choices, hint);
    }

    protected OverloadArmorSubmoduleConfigChoice choice(Tag value, Component label) {
        return new OverloadArmorSubmoduleConfigChoice(value, label);
    }

    protected List<OverloadArmorSubmoduleConfigChoice> booleanChoices() {
        return List.of(
                choice(ByteTag.valueOf(false), Component.translatable("ae2lt.overload_armor.screen.flag.no")),
                choice(ByteTag.valueOf(true), Component.translatable("ae2lt.overload_armor.screen.flag.yes")));
    }

    protected <E extends Enum<E>> OverloadArmorSubmoduleConfig enumConfig(
            String key,
            Component label,
            E value,
            Class<E> enumType
    ) {
        return enumConfig(key, label, value, enumType, enumValue -> Component.literal(formatOptionLabel(enumValue.name())));
    }

    protected <E extends Enum<E>> OverloadArmorSubmoduleConfig enumConfig(
            String key,
            Component label,
            E value,
            Class<E> enumType,
            Function<E, Component> valueLabeler
    ) {
        return config(
                key,
                label,
                StringTag.valueOf(value.name()),
                enumChoices(enumType, valueLabeler),
                Component.translatable("ae2lt.overload_armor.config.cycle_hint"));
    }

    protected <E extends Enum<E>> List<OverloadArmorSubmoduleConfigChoice> enumChoices(Class<E> enumType) {
        return enumChoices(enumType, enumValue -> Component.literal(formatOptionLabel(enumValue.name())));
    }

    protected <E extends Enum<E>> List<OverloadArmorSubmoduleConfigChoice> enumChoices(
            Class<E> enumType,
            Function<E, Component> valueLabeler
    ) {
        var choices = new ArrayList<OverloadArmorSubmoduleConfigChoice>();
        for (E enumValue : enumType.getEnumConstants()) {
            choices.add(choice(StringTag.valueOf(enumValue.name()), valueLabeler.apply(enumValue)));
        }
        return List.copyOf(choices);
    }

    protected static boolean isBooleanOption(Tag tag) {
        return tag instanceof ByteTag byteTag
                && (byteTag.getAsByte() == 0 || byteTag.getAsByte() == 1);
    }

    protected static Component formatOptionValue(Tag tag) {
        if (isBooleanOption(tag)) {
            return Component.translatable(((ByteTag) tag).getAsByte() != 0
                    ? "ae2lt.overload_armor.screen.flag.yes"
                    : "ae2lt.overload_armor.screen.flag.no");
        }
        if (tag instanceof NumericTag numericTag) {
            return Component.literal(String.valueOf(numericTag.getAsNumber()));
        }
        if (tag instanceof StringTag stringTag) {
            return Component.literal(stringTag.getAsString());
        }
        if (tag instanceof ListTag listTag) {
            return Component.literal("[" + listTag.size() + "]");
        }
        if (tag instanceof CompoundTag compoundTag) {
            return Component.literal("{" + compoundTag.getAllKeys().size() + "}");
        }
        return Component.literal(tag.getAsString());
    }

    protected static String formatOptionLabel(String key) {
        var normalized = key.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return key;
        }

        var words = normalized.split("\\s+");
        var builder = new StringBuilder();
        for (int index = 0; index < words.length; index++) {
            var word = words[index];
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                builder.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    @Nullable
    private static Component defaultHint(OverloadArmorSubmoduleConfig config) {
        if (!config.editable()) {
            return null;
        }
        return isBooleanChoiceSet(config.choices())
                ? Component.translatable("ae2lt.overload_armor.config.toggle_hint")
                : Component.translatable("ae2lt.overload_armor.config.cycle_hint");
    }

    private static boolean isBooleanChoiceSet(List<OverloadArmorSubmoduleConfigChoice> choices) {
        if (choices.size() != 2) {
            return false;
        }
        boolean hasFalse = false;
        boolean hasTrue = false;
        for (var choice : choices) {
            if (choice.value() instanceof ByteTag byteTag && byteTag.getAsByte() == 0) {
                hasFalse = true;
            } else if (choice.value() instanceof ByteTag byteTag && byteTag.getAsByte() == 1) {
                hasTrue = true;
            }
        }
        return hasFalse && hasTrue;
    }
}
