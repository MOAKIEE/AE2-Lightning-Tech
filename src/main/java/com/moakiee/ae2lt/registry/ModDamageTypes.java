package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * Pure resource-key holder for our custom damage types. The actual JSON for
 * {@code data/ae2lt/damage_type/electromagnetic.json} carries the message id
 * and exhaustion settings. We do per-hit armor bypass manually (see
 * {@code RailgunDamageCalculator}), so the JSON does NOT carry bypasses_armor.
 */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> ELECTROMAGNETIC = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "electromagnetic"));

    private ModDamageTypes() {}
}
