package com.moakiee.ae2lt.overload.armor.module;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public final class UndyingSubmodule extends AbstractOverloadArmorSubmodule {

    public static final UndyingSubmodule INSTANCE = new UndyingSubmodule();

    private static final String TAG_COMBO_UNTIL = "UndyingComboUntil";
    private static final String TAG_COMBO_COUNT = "UndyingComboCount";

    private UndyingSubmodule() {
    }

    @Override
    public String id() {
        return "undying";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.undying.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.undying.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

    public static int nextComboIndex(ItemStack armor, long gameTime) {
        return getComboUntil(armor) >= gameTime ? Math.max(1, getComboCount(armor) + 1) : 1;
    }

    public static void recordTrigger(ItemStack armor, long gameTime, int comboWindowTicks, int comboIndex) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        data.putLong(TAG_COMBO_UNTIL, gameTime + Math.max(1, comboWindowTicks));
        data.putInt(TAG_COMBO_COUNT, Math.max(1, comboIndex));
        OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
    }

    private static int getComboCount(ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        return data.contains(TAG_COMBO_COUNT, CompoundTag.TAG_INT) ? data.getInt(TAG_COMBO_COUNT) : 0;
    }

    private static long getComboUntil(ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        return data.contains(TAG_COMBO_UNTIL, CompoundTag.TAG_LONG) ? data.getLong(TAG_COMBO_UNTIL) : -1L;
    }

}
