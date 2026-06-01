package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.overload.armor.service.ArmorEnergyService;
import com.moakiee.ae2lt.overload.armor.service.ArmorLightningService;

public final class DashSubmodule extends AbstractOverloadArmorSubmodule {

    public static final DashSubmodule INSTANCE = new DashSubmodule();

    private static final double IMPULSE = 1.8D;
    private static final int COOLDOWN_TICKS = 40;
    private static final String TAG_COOLDOWN = "DashCooldown";

    private DashSubmodule() {}

    @Override
    public String id() {
        return "dash";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.dash.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.dash.desc";
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
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            setCooldown(armor, 0);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        int cd = getCooldown(armor);
        if (cd > 0) {
            setCooldown(armor, cd - 1);
        }
        return 0;
    }

    public static void applyDash(ServerPlayer player, ItemStack armor) {
        var sub = INSTANCE;
        if (!sub.isActive(armor)) return;
        if (getCooldown(armor) > 0) {
            player.displayClientMessage(Component.translatable("ae2lt.overload_armor.feature.dash.cooldown"), true);
            return;
        }
        long feCost = ArmorOverloadRules.DASH_ACTIVE_COST_FE;
        var payment = ArmorEnergyService.consumeActiveCostPayment(player, armor, feCost);
        if (!payment.paid()) {
            player.displayClientMessage(Component.translatable("ae2lt.overload_armor.fail.no_fe"), true);
            return;
        }
        if (!ArmorLightningService.consume(
                player,
                armor,
                com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE,
                AE2LTCommonConfig.overloadArmorDashHvCost())) {
            payment.refund();
            player.displayClientMessage(Component.translatable("ae2lt.overload_armor.fail.no_lightning"), true);
            return;
        }

        var look = player.getLookAngle();
        player.setDeltaMovement(
                player.getDeltaMovement().x + look.x * IMPULSE,
                Math.max(player.getDeltaMovement().y, 0.0D) + 0.3D,
                player.getDeltaMovement().z + look.z * IMPULSE);
        player.hurtMarked = true;
        player.resetFallDistance();
        setCooldown(armor, COOLDOWN_TICKS);
    }

    public static int getCooldown(ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        return data.contains(TAG_COOLDOWN, CompoundTag.TAG_INT) ? data.getInt(TAG_COOLDOWN) : 0;
    }

    private static void setCooldown(ItemStack armor, int ticks) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        if (ticks <= 0) {
            data.remove(TAG_COOLDOWN);
        } else {
            data.putInt(TAG_COOLDOWN, ticks);
        }
        OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
    }
}
