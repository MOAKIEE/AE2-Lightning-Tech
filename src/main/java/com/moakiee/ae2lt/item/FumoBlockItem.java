package com.moakiee.ae2lt.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class FumoBlockItem extends BlockItem {
    private final String tooltipKey;

    public FumoBlockItem(Block block, Item.Properties properties, String tooltipKey) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipComponents, tooltipFlag);
        tooltipComponents.accept(Component.translatable(tooltipKey + ".1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable(tooltipKey + ".2").withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable(tooltipKey + ".3").withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable(tooltipKey + ".4").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
