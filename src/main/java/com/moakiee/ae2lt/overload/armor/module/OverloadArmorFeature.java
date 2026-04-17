package com.moakiee.ae2lt.overload.armor.module;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public record OverloadArmorFeature(
        String id,
        String nameKey,
        String descriptionKey,
        boolean defaultEnabled,
        int idleLoad,
        boolean grantsTerminalAccess
) implements OverloadArmorSubmodule {
    public Component name() {
        return Component.translatable(nameKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }

    public Component buttonLabel(boolean enabled) {
        return Component.translatable(
                enabled
                        ? "ae2lt.overload_armor.feature.toggle.on"
                        : "ae2lt.overload_armor.feature.toggle.off",
                name());
    }

    @Override
    public int getIdleOverloaded(@Nullable Player player, Dist dist, ItemStack armor) {
        return idleLoad;
    }

    @Override
    public List<OverloadArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return List.of();
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        return false;
    }

    @Override
    public List<OverloadArmorSubmoduleOptionUi> getConfigUI(ItemStack armor) {
        return List.of();
    }
}
