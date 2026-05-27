package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public final class UndyingSubmodule extends AbstractOverloadArmorSubmodule {

    public static final UndyingSubmodule INSTANCE = new UndyingSubmodule();

    private static final String TAG_COOLDOWN = "UndyingCooldown";
    private static final String TAG_COOLDOWN_LAST_TICK = "UndyingCooldownLastTick";
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

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (dist == Dist.DEDICATED_SERVER) {
            tickCooldown(player, armor);
            if (player != null && getComboUntil(armor) < player.level().getGameTime()) {
                clearCombo(armor);
            }
        }
        return 0;
    }

    public static int getCooldown(ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        return data.contains(TAG_COOLDOWN, CompoundTag.TAG_INT) ? data.getInt(TAG_COOLDOWN) : 0;
    }

    public static void setCooldown(ItemStack armor, int ticks) {
        setCooldown(armor, ticks, -1L);
    }

    public static void setCooldown(ItemStack armor, int ticks, long gameTime) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        if (ticks <= 0) {
            data.remove(TAG_COOLDOWN);
            data.remove(TAG_COOLDOWN_LAST_TICK);
        } else {
            data.putInt(TAG_COOLDOWN, ticks);
            if (gameTime >= 0L) {
                data.putLong(TAG_COOLDOWN_LAST_TICK, gameTime);
            } else {
                data.remove(TAG_COOLDOWN_LAST_TICK);
            }
        }
        OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
    }

    private static void tickCooldown(@Nullable Player player, ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        int cooldown = data.contains(TAG_COOLDOWN, CompoundTag.TAG_INT) ? data.getInt(TAG_COOLDOWN) : 0;
        if (cooldown <= 0) {
            if (data.contains(TAG_COOLDOWN_LAST_TICK, CompoundTag.TAG_LONG)) {
                data.remove(TAG_COOLDOWN_LAST_TICK);
                OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
            }
            return;
        }
        if (player != null) {
            long gameTime = player.level().getGameTime();
            long lastTick = data.contains(TAG_COOLDOWN_LAST_TICK, CompoundTag.TAG_LONG)
                    ? data.getLong(TAG_COOLDOWN_LAST_TICK)
                    : -1L;
            if (lastTick >= gameTime) {
                return;
            }
            data.putLong(TAG_COOLDOWN_LAST_TICK, gameTime);
        }
        cooldown--;
        if (cooldown <= 0) {
            data.remove(TAG_COOLDOWN);
            data.remove(TAG_COOLDOWN_LAST_TICK);
        } else {
            data.putInt(TAG_COOLDOWN, cooldown);
        }
        OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
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

    private static void clearCombo(ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        data.remove(TAG_COMBO_UNTIL);
        data.remove(TAG_COMBO_COUNT);
        OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
    }
}
