package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.resources.ResourceLocation;

public final class SpeedSubmodule extends AbstractOverloadArmorSubmodule {

    public static final SpeedSubmodule INSTANCE = new SpeedSubmodule();

    private static final int IDLE_LOAD = 8;
    private static final double SPEED_BONUS = 0.3D;
    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "armor_submodule_speed");

    private SpeedSubmodule() {}

    @Override
    public String id() {
        return "speed";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.speed.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.speed.desc";
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
            var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null && attr.getModifier(MODIFIER_ID) == null) {
                attr.addTransientModifier(new AttributeModifier(
                        MODIFIER_ID, SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(MODIFIER_ID);
            }
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }
}
