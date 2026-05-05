package com.moakiee.ae2lt.client;

import java.util.List;

import com.moakiee.ae2lt.menu.FrequencyBindingMenu;
import com.moakiee.ae2lt.network.OpenFrequencyMenuPacket;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FrequencyBindingClient {
    private FrequencyBindingClient() {
    }

    public static TextureToggleButton createToolbarButton(FrequencyBindingMenu menu) {
        var button = new TextureToggleButton(
                TextureToggleButton.ButtonType.FREQUENCY_BIND,
                ignored -> PacketDistributor.sendToServer(new OpenFrequencyMenuPacket(
                        menu.getFrequencyBindingToken(),
                        menu.getFrequencyBindingBlockPos())));
        button.setTooltipAt(0, List.of(Component.translatable("ae2lt.gui.frequency.bind")));
        return button;
    }
}
