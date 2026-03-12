package com.moakiee.ae2lt.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;

/**
 * Client-side screen for the Overloaded Pattern Provider.
 * <p>
 * Currently reuses vanilla PatternProviderScreen layout and buttons.
 * Custom mode / auto-return / strategy toggles will be added here later.
 */
public class OverloadedPatternProviderScreen extends PatternProviderScreen<OverloadedPatternProviderMenu> {

    public OverloadedPatternProviderScreen(OverloadedPatternProviderMenu menu, Inventory playerInventory,
                                           Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
