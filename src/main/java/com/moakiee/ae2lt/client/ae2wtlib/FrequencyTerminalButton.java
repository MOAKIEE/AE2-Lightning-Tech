package com.moakiee.ae2lt.client.ae2wtlib;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.client.FrequencyBindingClient;
import com.moakiee.ae2lt.client.TextureToggleButton;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;

public final class FrequencyTerminalButton {

    private FrequencyTerminalButton() {
    }

    public static boolean shouldInject(AEBaseScreen<?> screen) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            return false;
        }

        var type = screen.getMenu().getType();
        var key = BuiltInRegistries.MENU.getKey(type);
        return key != null && key.getNamespace().equals("ae2wtlib");
    }

    public static ToolbarButtons addToToolbar(AEBaseScreen<?> screen) {
        var toolbar = ((AEBaseScreenAccessor) screen).ae2lt$getVerticalToolbar();
        var buttons = new ToolbarButtons(
                FrequencyBindingClient.createCardToolbarButton(),
                FrequencyBindingClient.createCardAutoConnectToolbarButton());
        toolbar.add(buttons.configureButton());
        toolbar.add(buttons.autoConnectButton());
        buttons.update(screen);
        return buttons;
    }

    private static ItemStack findInstalledFrequencyCard(AEBaseScreen<?> screen) {
        for (var slot : screen.getMenu().getSlots(SlotSemantics.UPGRADE)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof OverloadedFrequencyCardItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public record ToolbarButtons(TextureToggleButton configureButton, TextureToggleButton autoConnectButton) {
        public void update(AEBaseScreen<?> screen) {
            var card = findInstalledFrequencyCard(screen);
            boolean hasCard = !card.isEmpty();
            configureButton.setVisibility(hasCard);
            autoConnectButton.setVisibility(hasCard);
            if (hasCard) {
                autoConnectButton.setState(OverloadedFrequencyCardItem.getData(card).autoConnect());
            }
        }
    }
}
