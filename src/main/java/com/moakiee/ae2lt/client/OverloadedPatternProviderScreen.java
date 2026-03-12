package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;

import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;

/**
 * Client-side screen for the Overloaded Pattern Provider.
 * <p>
 * Adds three toggle buttons to the left toolbar (below the parent's buttons):
 * <ul>
 *   <li>Mode: Normal / Wireless (always visible)</li>
 *   <li>Auto-Return: On / Off (always visible)</li>
 *   <li>Wireless Strategy: Single Target / Even Distribution (visible only in wireless mode)</li>
 * </ul>
 * Button states are kept in sync via {@code @GuiSync} fields on the menu.
 */
public class OverloadedPatternProviderScreen extends PatternProviderScreen<OverloadedPatternProviderMenu> {

    private final ToggleButton modeButton;
    private final ToggleButton autoReturnButton;
    private final ToggleButton wirelessStrategyButton;

    public OverloadedPatternProviderScreen(OverloadedPatternProviderMenu menu, Inventory playerInventory,
                                           Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        // Mode toggle: Normal ↔ Wireless
        this.modeButton = new ToggleButton(
                Icon.SCHEDULING_ROUND_ROBIN, Icon.SCHEDULING_DEFAULT,
                btn -> menu.clientToggleMode());
        this.modeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.provider_mode.wireless")));
        this.modeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.provider_mode.normal")));
        addToLeftToolbar(this.modeButton);

        // Auto-Return toggle: On ↔ Off
        this.autoReturnButton = new ToggleButton(
                Icon.AUTO_EXPORT_ON, Icon.AUTO_EXPORT_OFF,
                btn -> menu.clientToggleAutoReturn());
        this.autoReturnButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.auto_return.on")));
        this.autoReturnButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.auto_return.off")));
        addToLeftToolbar(this.autoReturnButton);

        // Wireless Strategy toggle: Single Target ↔ Even Distribution
        this.wirelessStrategyButton = new ToggleButton(
                Icon.SCHEDULING_ROUND_ROBIN, Icon.SCHEDULING_DEFAULT,
                btn -> menu.clientToggleWirelessStrategy());
        this.wirelessStrategyButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.wireless_strategy.even")));
        this.wirelessStrategyButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.wireless_strategy.single")));
        addToLeftToolbar(this.wirelessStrategyButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        // Sync button visual states from server-synced menu fields
        this.modeButton.setState(this.menu.isWirelessMode());
        this.autoReturnButton.setState(this.menu.isAutoReturnEnabled());
        this.wirelessStrategyButton.setState(this.menu.isEvenDistribution());

        // Hide wireless strategy button when in normal mode
        this.wirelessStrategyButton.setVisibility(this.menu.isWirelessMode());
    }
}
