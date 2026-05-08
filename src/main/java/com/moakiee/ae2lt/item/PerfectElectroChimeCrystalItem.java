package com.moakiee.ae2lt.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class PerfectElectroChimeCrystalItem extends Item {
    public PerfectElectroChimeCrystalItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable(
                "item.ae2lt.perfect_electro_chime_crystal.complete").withStyle(ChatFormatting.AQUA));
    }
}
