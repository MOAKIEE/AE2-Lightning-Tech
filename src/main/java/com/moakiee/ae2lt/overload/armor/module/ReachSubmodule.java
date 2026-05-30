package com.moakiee.ae2lt.overload.armor.module;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ReachSubmodule extends AbstractOverloadArmorSubmodule {

    public static final ReachSubmodule INSTANCE = new ReachSubmodule();

    private ReachSubmodule() {
    }

    @Override
    public String id() {
        return "reach_extension";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.reach_extension.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.reach_extension.desc";
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
    public List<OverloadArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return List.of(rangeConfig(armor));
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (ReachDistanceOption.CONFIG_KEY.equals(key)) {
            var option = ReachDistanceOption.fromTag(value);
            var options = getOptions(armor);
            options.put(ReachDistanceOption.CONFIG_KEY, option.toTag());
            setOptions(armor, options);
            return true;
        }
        return false;
    }

    public static ReachDistanceOption selectedRange(ItemStack armor) {
        return INSTANCE.getSelectedRange(armor);
    }

    public static double blockBonus(ItemStack armor) {
        return selectedRange(armor).blockBonus();
    }

    public static double entityBonus(ItemStack armor) {
        return selectedRange(armor).entityBonus();
    }

    private OverloadArmorSubmoduleConfig rangeConfig(ItemStack armor) {
        return config(
                ReachDistanceOption.CONFIG_KEY,
                Component.translatable("ae2lt.overload_armor.config.reach_range"),
                getSelectedRange(armor).toTag(),
                rangeChoices(),
                Component.translatable("ae2lt.overload_armor.config.reach_range.hint"));
    }

    private List<OverloadArmorSubmoduleConfigChoice> rangeChoices() {
        return List.of(
                choice(ReachDistanceOption.ONE.toTag(), Component.literal(ReachDistanceOption.ONE.label())),
                choice(ReachDistanceOption.TWO.toTag(), Component.literal(ReachDistanceOption.TWO.label())),
                choice(ReachDistanceOption.FOUR.toTag(), Component.literal(ReachDistanceOption.FOUR.label())));
    }

    private ReachDistanceOption getSelectedRange(ItemStack armor) {
        var options = getOptions(armor);
        return ReachDistanceOption.fromTag(options.get(ReachDistanceOption.CONFIG_KEY));
    }
}
