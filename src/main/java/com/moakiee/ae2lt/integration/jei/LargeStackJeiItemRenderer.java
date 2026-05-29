package com.moakiee.ae2lt.integration.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;

import mezz.jei.api.ingredients.IIngredientRenderer;

public class LargeStackJeiItemRenderer implements IIngredientRenderer<ItemStack> {
    public static final LargeStackJeiItemRenderer INSTANCE = new LargeStackJeiItemRenderer();

    private LargeStackJeiItemRenderer() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, ItemStack ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return;
        }
        guiGraphics.renderFakeItem(ingredient, 0, 0);
        LargeStackCountRenderer.renderCountAt(guiGraphics, getFontRenderer(Minecraft.getInstance(), ingredient), 0, 0, ingredient.getCount());
    }

    @Override
    public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
        Minecraft minecraft = Minecraft.getInstance();
        return ingredient.getTooltipLines(minecraft.player, tooltipFlag);
    }

    @Override
    public Font getFontRenderer(Minecraft minecraft, ItemStack ingredient) {
        return minecraft.font;
    }
}
