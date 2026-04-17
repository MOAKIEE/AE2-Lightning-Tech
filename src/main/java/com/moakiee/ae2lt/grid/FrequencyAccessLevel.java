package com.moakiee.ae2lt.grid;

import net.minecraft.ChatFormatting;

import javax.annotation.Nonnull;

public enum FrequencyAccessLevel {
    OWNER(ChatFormatting.DARK_PURPLE),
    ADMIN(ChatFormatting.BLUE),
    USER(ChatFormatting.GREEN),
    BLOCKED(ChatFormatting.RED);

    public static final FrequencyAccessLevel[] VALUES = values();

    private final ChatFormatting formatting;

    FrequencyAccessLevel(ChatFormatting formatting) {
        this.formatting = formatting;
    }

    @Nonnull
    public static FrequencyAccessLevel fromId(byte id) {
        if (id < 0 || id >= VALUES.length) return BLOCKED;
        return VALUES[id];
    }

    public byte getId() {
        return (byte) ordinal();
    }

    public ChatFormatting getFormatting() {
        return formatting;
    }

    public boolean canUse() {
        return this != BLOCKED;
    }

    public boolean canEdit() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canDelete() {
        return this == OWNER;
    }
}
