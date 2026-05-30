package com.moakiee.ae2lt.overload.armor.module;

import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public enum ReachDistanceOption {
    ONE("1x", 8.0D, 5.0D),
    TWO("2x", 24.0D, 10.0D),
    FOUR("4x", 48.0D, 16.0D);

    public static final String CONFIG_KEY = "reach_range";

    private final String label;
    private final double blockBonus;
    private final double entityBonus;

    ReachDistanceOption(String label, double blockBonus, double entityBonus) {
        this.label = label;
        this.blockBonus = blockBonus;
        this.entityBonus = entityBonus;
    }

    public String id() {
        return name();
    }

    public String label() {
        return label;
    }

    public double blockBonus() {
        return blockBonus;
    }

    public double entityBonus() {
        return entityBonus;
    }

    public StringTag toTag() {
        return StringTag.valueOf(id());
    }

    public static ReachDistanceOption fromTag(Tag tag) {
        return tag instanceof StringTag stringTag
                ? fromId(stringTag.getAsString())
                : ONE;
    }

    public static ReachDistanceOption fromId(String id) {
        if (id != null) {
            for (ReachDistanceOption option : values()) {
                if (option.id().equalsIgnoreCase(id) || option.label.equalsIgnoreCase(id)) {
                    return option;
                }
            }
        }
        return ONE;
    }
}
