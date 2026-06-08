package com.moakiee.ae2lt.client.ae2wtlib;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.IconTabButton;
import com.moakiee.ae2lt.network.OpenFrequencyCardMenuPacket;

/**
 * Injects a small "configure frequency card" button into every ae2wtlib
 * wireless terminal screen. Clicking it opens the card-mode frequency menu for
 * the frequency card installed in that terminal; the server rejects the request
 * (with a message) when no card is installed.
 *
 * <p>Only the menu type's namespace is inspected, so this class does not need to
 * reference ae2wtlib types and is safe to keep registered unconditionally.</p>
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class FrequencyTerminalButton {

    // AE2 TabButton renders at a fixed 22×22 footprint; mirror it so the
    // bottom-left placement lines up with the GUI panel edge.
    private static final int BUTTON_SIZE = 22;

    private static final ResourceLocation FREQUENCY_ICON = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/buttons/frequency_connect.png");

    private FrequencyTerminalButton() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            return;
        }
        if (!(event.getScreen() instanceof AEBaseScreen<?> screen)) {
            return;
        }

        var type = screen.getMenu().getType();
        var key = BuiltInRegistries.MENU.getKey(type);
        if (key == null || !key.getNamespace().equals("ae2wtlib")) {
            return;
        }

        int token = screen.getMenu().containerId;
        // Place the button in the screen's left toolbar column, bottom-aligned
        // with the GUI panel. AE2 centers its GUIs vertically, so the panel
        // height can be derived from the window height and top inset.
        int guiHeight = screen.height - 2 * screen.getGuiTop();
        int x = screen.getGuiLeft() - BUTTON_SIZE;
        int y = screen.getGuiTop() + guiHeight - BUTTON_SIZE;

        Component tooltip = Component.translatable("ae2lt.gui.button.open_frequency_card");
        IconTabButton button = new IconTabButton(
                FREQUENCY_ICON,
                tooltip,
                btn -> PacketDistributor.sendToServer(new OpenFrequencyCardMenuPacket(token)));
        button.setX(x);
        button.setY(y);
        button.setTooltip(Tooltip.create(tooltip));
        event.addListener(button);
    }
}
