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

/**
 * Applies staged mitigation and reflect tuning from active armor modules.
 *
 * <p>{@code passRate} is applied multiplicatively after vanilla armor in Pre.
 * {@code reflectPct} bounces post-resist damage back to LivingEntity attackers in Post.
 * Environmental damage (fire/fall/drown) is never reflected.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadArmorDamageHandler {

    private OverloadArmorDamageHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        double passRate = collectPassRate(player);
        if (passRate < 1.0D) {
            event.setNewDamage(event.getOriginalDamage() * (float) passRate);
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

    private static double collectPassRate(Player player) {
        double passRate = 1.0D;
        for (var cap : collectCapabilities(player)) {
            if (cap instanceof DeviceCapability.StagedMitigation mitigation) {
                passRate *= Math.clamp(mitigation.passRate(), 0.0D, 1.0D);
            }
        }
        return passRate;
    }

    private static double collectReflect(Player player) {
        double total = 0.0D;
        for (var cap : collectCapabilities(player)) {
            if (cap instanceof DeviceCapability.ReflectTuning reflect && reflect.reflectPct() > 0.0D) {
                total = Math.min(1.0D, total + reflect.reflectPct());
            }
        }
        return total;
    }

    private static java.util.List<DeviceCapability> collectCapabilities(Player player) {
        var out = new java.util.ArrayList<DeviceCapability>();
        for (EquipmentSlot slot : java.util.List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty() || !(armor.getItem() instanceof BaseOverloadArmorItem)) {
                continue;
            }
            var snapshot = OverloadArmorState.snapshot(player, armor, player.level().registryAccess(), true);
            if (!snapshot.hasCore() || snapshot.locked()) {
                continue;
            }
            var stacks = OverloadArmorState.loadModuleStacks(armor, player.level().registryAccess());
            for (ItemStack s : stacks) {
                if (!s.isEmpty() && s.getItem() instanceof OverloadDeviceModuleItem m) {
                    out.addAll(m.capabilities(s));
                }
            }
        }
        return out;
    }
}
