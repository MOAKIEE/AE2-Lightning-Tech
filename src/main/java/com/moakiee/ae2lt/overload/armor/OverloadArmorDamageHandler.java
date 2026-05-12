package com.moakiee.ae2lt.overload.armor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.item.OverloadArmorItem;

/**
 * Applies DamageMitigation (resist + reflect) from armor module capabilities.
 *
 * <p>{@code resistPct} is applied multiplicatively after vanilla armor in Pre.
 * {@code reflectPct} bounces post-resist damage back to LivingEntity attackers in Post.
 * Environmental damage (fire/fall/drown) is never reflected.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadArmorDamageHandler {

    private OverloadArmorDamageHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        double resist = collectResist(player);
        if (resist > 0.0D) {
            event.setNewDamage(event.getOriginalDamage() * (float) (1.0D - resist));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        double reflect = collectReflect(player);
        if (reflect <= 0.0D) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        float reflected = event.getOriginalDamage() * (float) reflect;
        if (reflected > 0.0F) {
            attacker.hurt(event.getSource(), reflected);
        }
    }

    private static double collectResist(Player player) {
        double total = 0.0D;
        for (var cap : collectCapabilities(player)) {
            if (cap instanceof DeviceCapability.DamageMitigation dm && dm.resistPct() > 0.0D) {
                total = 1.0D - (1.0D - total) * (1.0D - dm.resistPct());
            }
        }
        return total;
    }

    private static double collectReflect(Player player) {
        double total = 0.0D;
        for (var cap : collectCapabilities(player)) {
            if (cap instanceof DeviceCapability.DamageMitigation dm && dm.reflectPct() > 0.0D) {
                total = Math.min(1.0D, total + dm.reflectPct());
            }
        }
        return total;
    }

    private static java.util.List<DeviceCapability> collectCapabilities(Player player) {
        ItemStack armor = player.getItemBySlot(EquipmentSlot.CHEST);
        if (armor.isEmpty() || !(armor.getItem() instanceof OverloadArmorItem)) {
            return java.util.List.of();
        }
        var stacks = OverloadArmorState.loadModuleStacks(armor, player.level().registryAccess());
        var out = new java.util.ArrayList<DeviceCapability>();
        for (ItemStack s : stacks) {
            if (!s.isEmpty() && s.getItem() instanceof OverloadDeviceModuleItem m) {
                out.addAll(m.capabilities(s));
            }
        }
        return out;
    }
}
