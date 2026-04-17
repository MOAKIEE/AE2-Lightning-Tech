package com.moakiee.ae2lt.integration.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;

import mezz.jei.api.ingredients.IIngredientRenderer;

public class LargeStackJeiItemRenderer implements IIngredientRenderer<ItemStack> {
    public static final LargeStackJeiItemRenderer INSTANCE = new LargeStackJeiItemRenderer();

    private LargeStackJeiItemRenderer() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, ItemStack ingredient) {
        render(guiGraphics, ingredient, 0, 0);
    }

    @Override
    public void render(GuiGraphics guiGraphics, ItemStack ingredient, int posX, int posY) {
        if (ingredient == null || ingredient.isEmpty()) {
            return;
        }

        RenderSystem.enableDepthTest();
        guiGraphics.renderFakeItem(ingredient, posX, posY);
        LargeStackCountRenderer.renderCountAt(guiGraphics, getFontRenderer(Minecraft.getInstance(), ingredient), posX, posY, ingredient.getCount());
        RenderSystem.disableBlend();
    }

    @Override
    public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
        Minecraft minecraft = Minecraft.getInstance();
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
        return ingredient.getTooltipLines(tooltipContext, minecraft.player, tooltipFlag);
    }

    @Override
    public Font getFontRenderer(Minecraft minecraft, ItemStack ingredient) {
        return minecraft.font;
    }
}
