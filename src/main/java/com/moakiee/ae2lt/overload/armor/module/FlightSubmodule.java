package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class FlightSubmodule extends AbstractOverloadArmorSubmodule {

    public static final FlightSubmodule INSTANCE = new FlightSubmodule();

    private static final int IDLE_LOAD = 64;

    private FlightSubmodule() {}

    @Override
    public String id() {
        return "flight";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.flight.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.flight.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getIdleOverloaded(@Nullable Player player, Dist dist, ItemStack armor) {
        return IDLE_LOAD;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            grantFlight(player);
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            revokeFlight(player);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        // Keep flight ability alive; fall back if armor removed unexpectedly.
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            if (!player.getAbilities().mayfly) {
                grantFlight(player);
            }
        }
        return 0;
    }

    private static void grantFlight(Player player) {
        var abilities = player.getAbilities();
        abilities.mayfly = true;
        player.onUpdateAbilities();
    }

    private static void revokeFlight(Player player) {
        var abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();
    }
}
