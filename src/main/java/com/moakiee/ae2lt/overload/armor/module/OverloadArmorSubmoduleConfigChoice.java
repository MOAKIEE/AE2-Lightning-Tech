package com.moakiee.ae2lt.overload.armor.module;

import java.util.Objects;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public record OverloadArmorSubmoduleConfigChoice(
        Tag value,
        Component label
) {
    public OverloadArmorSubmoduleConfigChoice {
        value = Objects.requireNonNull(value, "value").copy();
        Objects.requireNonNull(label, "label");
    }

    public boolean matches(Tag candidate) {
        return value.equals(candidate);
    }
}
