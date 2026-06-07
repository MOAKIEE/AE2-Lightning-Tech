package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class ResistanceSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final ResistanceSubmodule T1 = new ResistanceSubmodule(
            "matrix_shield",
            "ae2lt.celestweave.feature.matrix_shield.name",
            "ae2lt.celestweave.feature.matrix_shield.desc");
    public static final ResistanceSubmodule T2 = new ResistanceSubmodule(
            "phase_shield",
            "ae2lt.celestweave.feature.phase_shield.name",
            "ae2lt.celestweave.feature.phase_shield.desc");

    public static final String INSTALL_GROUP = "mitigation";

    private final String id;
    private final String nameKey;
    private final String descriptionKey;

    private ResistanceSubmodule(String id, String nameKey, String descriptionKey) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String nameKey() {
        return nameKey;
    }

    @Override
    public String descriptionKey() {
        return descriptionKey;
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
    public String installGroupId() {
        return INSTALL_GROUP;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        // Resistance is applied via StagedMitigation in CelestweaveArmorDamageHandler.
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        // No persistent effect to remove; damage handler checks active state.
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }
}
