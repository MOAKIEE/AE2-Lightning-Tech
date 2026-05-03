package com.moakiee.ae2lt.item.railgun;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;

import com.moakiee.ae2lt.logic.railgun.RailgunFireService;
import com.moakiee.ae2lt.menu.railgun.RailgunHost;
import com.moakiee.ae2lt.registry.ModDataComponents;
import appeng.menu.locator.ItemMenuHostLocator;

public class ElectromagneticRailgunItem extends Item implements IMenuItem {

    /** Sentinel duration; we manage charging via {@link #onUseTick}. */
    private static final int USE_DURATION = 72_000;

    public ElectromagneticRailgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        RailgunModules mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULES.get(), RailgunModules.EMPTY);
        if (!mods.hasCore()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.railgun.core_required"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            stack.set(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        }
        return new InteractionResultHolder<>(InteractionResult.CONSUME, stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide) {
            return;
        }
        long current = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        stack.set(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), current + 1L);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide || !(user instanceof ServerPlayer player) || !(level instanceof ServerLevel sl)) {
            stack.remove(ModDataComponents.RAILGUN_CHARGE_TICKS.get());
            return;
        }
        long charged = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        stack.remove(ModDataComponents.RAILGUN_CHARGE_TICKS.get());
        RailgunModules mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULES.get(), RailgunModules.EMPTY);
        RailgunChargeTier tier = RailgunFireService.tierForCharge(charged, mods);
        if (tier == RailgunChargeTier.HV) {
            return;
        }
        RailgunFireService.fireCharged(sl, player, stack, tier);
    }

    @Override
    public @Nullable ItemMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new RailgunHost(this, player, locator);
    }
}
