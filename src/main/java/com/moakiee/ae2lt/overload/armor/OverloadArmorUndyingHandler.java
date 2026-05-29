package com.moakiee.ae2lt.overload.armor;

import java.util.List;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.module.UndyingSubmodule;
import com.moakiee.ae2lt.overload.armor.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.overload.armor.service.ArmorEnergyService;
import com.moakiee.ae2lt.overload.armor.service.ArmorLightningService;
import com.moakiee.ae2lt.registry.ModDamageTypes;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadArmorUndyingHandler {
    private static final String TAG_PROTECTED_TICK = "ae2lt.undying_protected_tick";
    private static final String TAG_PROTECTED_UNTIL = "ae2lt.undying_protected_until";
    private static final float RESTORE_HEALTH = 4.0F;
    private static final int CLEANSING_LIMIT = 3;
    private static final int PROTECTION_WINDOW_TICKS = 20;

    private OverloadArmorUndyingHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingFatalDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        float damage = event.getAmount();
        if (damage <= 0.0F
                || !isIncomingUndyingCandidate(event.getSource())
                || damage < player.getHealth() + player.getAbsorptionAmount()) {
            return;
        }
        long now = player.level().getGameTime();
        if (tryProtectWithinWindow(player, now)) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        } else if (tryTrigger(player, now)) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0.0F || damage < player.getHealth() + player.getAbsorptionAmount()) {
            return;
        }
        long now = player.level().getGameTime();
        if (tryProtectWithinWindow(player, now)) {
            event.setNewDamage(0.0F);
        } else if (tryTrigger(player, now)) {
            event.setNewDamage(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (tryProtectForcedDeath(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        tryProtectDeadOrDying(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        tryProtectDeadOrDying(event.getEntity());
    }

    private static void tryProtectDeadOrDying(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level().isClientSide()) {
            return;
        }
        if (serverPlayer.dead || serverPlayer.isDeadOrDying() || serverPlayer.getHealth() <= 0.0F) {
            tryProtectForcedDeath(serverPlayer);
        }
    }

    public static boolean tryProtectForcedDeath(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            return false;
        }
        if (wasProtectedThisTick(player)) {
            restoreSurvivalState(player);
            return true;
        }
        long now = player.level().getGameTime();
        if (tryProtectWithinWindow(player, now)) {
            return true;
        }
        return tryTrigger(player, now);
    }

    public static boolean wasProtectedThisTick(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        return player.getPersistentData().getLong(TAG_PROTECTED_TICK) == player.level().getGameTime();
    }

    private static boolean tryProtectWithinWindow(ServerPlayer player, long now) {
        if (hasActiveProtectionWindow(player, now)) {
            recordProtectedTick(player, now);
            restoreSurvivalState(player);
            return true;
        }
        return false;
    }

    private static boolean tryTrigger(ServerPlayer player, long now) {
        if (player.isSpectator()) {
            return false;
        }
        for (var active : collectActiveLastStand(player)) {
            int comboIndex = UndyingSubmodule.nextComboIndex(active.armor(), now);
            long cost = scaledCost(active.tuning().feCost(), comboIndex);
            ArmorEnergyService.EnergyPayment payment = ArmorEnergyService.consumeActiveCostPayment(
                    player,
                    active.armor(),
                    cost);
            if (!payment.paid()) {
                continue;
            }
            long lightningCost = scaledCost(AE2LTCommonConfig.overloadArmorUndyingEhvCost(), comboIndex);
            if (!ArmorLightningService.consume(
                    player,
                    active.armor(),
                    com.moakiee.ae2lt.me.key.LightningKey.EXTREME_HIGH_VOLTAGE,
                    lightningCost)) {
                payment.refund();
                continue;
            }
            UndyingSubmodule.recordTrigger(
                    active.armor(),
                    now,
                    Math.max(1, active.tuning().comboWindowTicks()),
                    comboIndex);
            recordProtectionWindow(player, now);
            restoreSurvivalState(player);
            cleanseHarmfulEffects(player, CLEANSING_LIMIT);
            return true;
        }
        return false;
    }

    private static boolean hasActiveProtectionWindow(ServerPlayer player, long now) {
        long protectedUntil = player.getPersistentData().getLong(TAG_PROTECTED_UNTIL);
        return protectedUntil > now;
    }

    private static boolean isIncomingUndyingCandidate(DamageSource source) {
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

    private static void recordProtectedTick(ServerPlayer player, long now) {
        player.getPersistentData().putLong(TAG_PROTECTED_TICK, now);
    }

    private static void recordProtectionWindow(ServerPlayer player, long now) {
        recordProtectedTick(player, now);
        player.getPersistentData().putLong(TAG_PROTECTED_UNTIL, saturatingAdd(now, PROTECTION_WINDOW_TICKS));
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static void restoreSurvivalState(ServerPlayer player) {
        player.dead = false;
        player.clearFire();
        player.setRemainingFireTicks(0);
        player.resetFallDistance();
        float targetHealth = Math.max(1.0F, Math.min(player.getMaxHealth(), RESTORE_HEALTH));
        if (player.getHealth() < targetHealth) {
            player.setHealth(targetHealth);
        }
        player.invulnerableTime = Math.max(player.invulnerableTime, 20);
        player.hurtTime = 0;
        player.hurtDuration = 0;
    }

    private static int cleanseHarmfulEffects(ServerPlayer player, int maxEffects) {
        int removed = 0;
        for (var effect : List.copyOf(player.getActiveEffects())) {
            if (removed >= maxEffects) {
                break;
            }
            if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }
            if (player.removeEffect(effect.getEffect())) {
                removed++;
            }
        }
        return removed;
    }

    private static List<ActiveLastStand> collectActiveLastStand(ServerPlayer player) {
        return ArmorCapabilityCollector.collectPerInstalledStack(player).stream()
                .flatMap(active -> {
                    if (active.capability() instanceof DeviceCapability.LastStandTuning tuning) {
                        return java.util.stream.Stream.of(new ActiveLastStand(
                                active.armor(),
                                active.submoduleId(),
                                tuning));
                    }
                    return java.util.stream.Stream.empty();
                })
                .toList();
    }

    private static long scaledCost(long baseCost, int comboIndex) {
        int safeCombo = Math.max(1, comboIndex);
        if (baseCost <= 0L) {
            return 0L;
        }
        if (baseCost > Long.MAX_VALUE / safeCombo) {
            return Long.MAX_VALUE;
        }
        return baseCost * safeCombo;
    }

    private record ActiveLastStand(
            ItemStack armor,
            String submoduleId,
            DeviceCapability.LastStandTuning tuning) {
    }
}
