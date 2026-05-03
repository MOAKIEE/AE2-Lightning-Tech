package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Renders a small charge-progress bar near the crosshair while the player is
 * holding right-click on the railgun.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunHudRenderer {

    private RailgunHudRenderer() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isUsingItem()) return;
        ItemStack stack = mc.player.getUseItem();
        if (!(stack.getItem() instanceof ElectromagneticRailgunItem)) return;

        long ticks = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        int t1 = RailgunDefaults.CHARGE_TICKS_TIER1;
        int t2 = RailgunDefaults.CHARGE_TICKS_TIER2;
        int t3 = RailgunDefaults.CHARGE_TICKS_TIER3;
        float progress = Math.min(1.0f, (float) ticks / (float) t3);

        int tier = ticks >= t3 ? 3 : ticks >= t2 ? 2 : ticks >= t1 ? 1 : 0;

        GuiGraphics gfx = e.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int barW = 80, barH = 6;
        int x = (w - barW) / 2;
        int y = h / 2 + 16;

        int color = switch (tier) {
            case 3 -> 0xFFB0FFFF;
            case 2 -> 0xFF7FCCFF;
            case 1 -> 0xFF55AAFF;
            default -> 0xFF334466;
        };
        gfx.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xCC000000);
        gfx.fill(x, y, x + (int) (barW * progress), y + barH, color);

        // Tier label
        Component label = Component.translatable("ae2lt.railgun.tier_" + tier);
        int lw = mc.font.width(label);
        gfx.drawString(mc.font, label, (w - lw) / 2, y + barH + 2, 0xFFFFFFFF, true);

        ensureUserStillUsing(mc.player, stack);
    }

    private static void ensureUserStillUsing(LivingEntity user, ItemStack stack) {
        // No-op; placeholder hook for future state validation.
    }
}
