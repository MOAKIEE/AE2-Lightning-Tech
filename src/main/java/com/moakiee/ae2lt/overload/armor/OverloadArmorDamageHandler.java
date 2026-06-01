package com.moakiee.ae2lt.overload.armor;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.overload.armor.service.ArmorCapabilityCollector.ActiveCapability;
import com.moakiee.ae2lt.overload.armor.service.ArmorEnergyService;
import com.moakiee.ae2lt.overload.armor.service.ArmorLightningService;
import com.moakiee.ae2lt.registry.ModDamageTypes;

/**
 * Applies staged mitigation and reflect tuning from active armor modules.
 *
 * <p>The active mitigation stage is applied after vanilla armor in Pre.
 * {@code reflectPct} bounces pre-overload-shield damage back to LivingEntity attackers.
 * Environmental damage (fire/fall/drown) is never reflected.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadArmorDamageHandler {
    private static final ThreadLocal<Boolean> REFLECTING_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private OverloadArmorDamageHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        float incoming = event.getNewDamage();
        var capabilities = ArmorCapabilityCollector.collectPerInstalledUnit(player);
        ActiveCapability mitigation = collectMitigation(capabilities);
        if (mitigation != null
                && mitigation.capability() instanceof DeviceCapability.StagedMitigation staged) {
            float afterMitigation = ArmorMitigationRules.apply(
                    staged.stage(),
                    classifyDamage(event.getSource()),
                    incoming);
            if (payMitigationLightning(player, mitigation, staged, incoming - afterMitigation)) {
                event.setNewDamage(afterMitigation);
            }
        }
        if (!isReflectingDamage()) {
            reflectIncomingDamage(player, event.getSource(), incoming);
        }
    }

    private static ActiveCapability collectMitigation(java.util.List<ActiveCapability> capabilities) {
        for (var active : capabilities) {
            if (active.capability() instanceof DeviceCapability.StagedMitigation) {
                return active;
            }
        }
        return null;
    }

    private static ArmorMitigationRules.DamageClass classifyDamage(DamageSource source) {
        if (isHardDamage(source)) {
            return ArmorMitigationRules.DamageClass.HARD;
        }
        if (isEnvironmentDamage(source)) {
            return ArmorMitigationRules.DamageClass.ENVIRONMENT;
        }
        return ArmorMitigationRules.DamageClass.ORDINARY;
    }

    private static boolean isHardDamage(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER)
                || source.is(DamageTypes.WITHER_SKULL)
                || source.is(ModDamageTypes.ELECTROMAGNETIC);
    }

    private static boolean isEnvironmentDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.HOT_FLOOR)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.DRY_OUT)
                || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.FREEZE)
                || source.is(DamageTypes.STALAGMITE);
    }

    private static boolean payMitigationLightning(
            Player player,
            ActiveCapability mitigation,
            DeviceCapability.StagedMitigation staged,
            float preventedDamage) {
        if (!(player instanceof ServerPlayer serverPlayer) || preventedDamage <= 0.0F) {
            return true;
        }
        long amount = (long) Math.ceil(preventedDamage * mitigationLightningPerDamage(staged.stage()));
        if (amount <= 0L) {
            return true;
        }
        var key = "phase_shield".equals(staged.stage())
                ? com.moakiee.ae2lt.me.key.LightningKey.EXTREME_HIGH_VOLTAGE
                : com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE;
        return ArmorLightningService.consume(serverPlayer, mitigation.armor(), key, amount);
    }

    private static long mitigationLightningPerDamage(String stage) {
        return "phase_shield".equals(stage)
                ? AE2LTCommonConfig.overloadArmorPhaseShieldEhvPerDamage()
                : AE2LTCommonConfig.overloadArmorMitigationHvPerDamage();
    }

    private static void reflectIncomingDamage(Player player, DamageSource source, float incomingDamage) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        float reflected = collectReflectedDamage(player, incomingDamage);
        if (reflected > 0.0F) {
            hurtWithReflectGuard(attacker, source, reflected);
        }
    }

    private static boolean isReflectingDamage() {
        return Boolean.TRUE.equals(REFLECTING_DAMAGE.get());
    }

    private static void hurtWithReflectGuard(LivingEntity target, DamageSource source, float damage) {
        boolean wasReflecting = isReflectingDamage();
        REFLECTING_DAMAGE.set(Boolean.TRUE);
        try {
            target.hurt(source, damage);
        } finally {
            REFLECTING_DAMAGE.set(wasReflecting);
        }
    }

    private static float collectReflectedDamage(Player player, float damage) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0.0F;
        }
        if (damage <= 0.0F) {
            return 0.0F;
        }
        float reflected = 0.0F;
        for (var active : ArmorCapabilityCollector.collectPerInstalledUnit(player)) {
            if (!(active.capability() instanceof DeviceCapability.ReflectTuning reflect)
                    || reflect.reflectPct() <= 0.0D) {
                continue;
            }
            float remaining = Math.max(0.0F, damage - reflected);
            float amount = Math.min(remaining, damage * (float) reflect.reflectPct());
            if (amount <= 0.0F) {
                continue;
            }
            long cost = (long) Math.ceil(amount * Math.max(0L, reflect.fePerDamage()));
            ArmorEnergyService.EnergyPayment payment = ArmorEnergyService.consumeActiveCostPayment(
                    serverPlayer,
                    active.armor(),
                    cost);
            if (!payment.paid()) {
                continue;
            }
            long lightningCost = (long) Math.ceil(amount * AE2LTCommonConfig.overloadArmorReflectHvPerDamage());
            if (!ArmorLightningService.consume(
                    serverPlayer,
                    active.armor(),
                    com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE,
                    lightningCost)) {
                payment.refund();
                continue;
            }
            reflected += amount;
            if (reflected >= damage) {
                break;
            }
        }
        return reflected;
    }

}
