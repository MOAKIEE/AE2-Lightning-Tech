package com.moakiee.ae2lt.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.PatternProviderMenu;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessStrategy;

/**
 * Menu (container) for the Overloaded Pattern Provider.
 * <p>
 * Extends vanilla PatternProviderMenu (slots, blocking mode, etc. fully reused).
 * Syncs three custom fields to the client Screen via {@code @GuiSync}.
 * Registers client actions so the Screen can toggle server-side state.
 */
public class OverloadedPatternProviderMenu extends PatternProviderMenu {

    public static final MenuType<OverloadedPatternProviderMenu> TYPE = MenuTypeBuilder
            .create(OverloadedPatternProviderMenu::new, PatternProviderLogicHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_pattern_provider"));

    // -- Synced fields (high ids to avoid parent class conflicts: parent uses 3-7) --

    /** 0 = NORMAL, 1 = WIRELESS */
    @GuiSync(10)
    public int providerMode;

    /** 0 = off, 1 = on */
    @GuiSync(11)
    public int autoReturn;

    /** 0 = SINGLE_TARGET, 1 = EVEN_DISTRIBUTION */
    @GuiSync(12)
    public int wirelessStrategy;

    private final PatternProviderLogicHost host;

    public OverloadedPatternProviderMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        // Register server-side handlers for client button clicks
        registerClientAction("toggleMode", this::toggleMode);
        registerClientAction("toggleAutoReturn", this::toggleAutoReturn);
        registerClientAction("toggleWirelessStrategy", this::toggleWirelessStrategy);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            providerMode = be.getProviderMode().ordinal();
            autoReturn = be.isAutoReturn() ? 1 : 0;
            wirelessStrategy = be.getWirelessStrategy().ordinal();
        }
        super.broadcastChanges();
    }

    // -- Server-side action handlers --

    private void toggleMode() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var current = be.getProviderMode();
            be.setProviderMode(current == ProviderMode.NORMAL ? ProviderMode.WIRELESS : ProviderMode.NORMAL);
        }
    }

    private void toggleAutoReturn() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            be.setAutoReturn(!be.isAutoReturn());
        }
    }

    private void toggleWirelessStrategy() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var current = be.getWirelessStrategy();
            be.setWirelessStrategy(current == WirelessStrategy.SINGLE_TARGET
                    ? WirelessStrategy.EVEN_DISTRIBUTION : WirelessStrategy.SINGLE_TARGET);
        }
    }

    // -- Public forwarding methods for Screen button callbacks --

    public void clientToggleMode() {
        sendClientAction("toggleMode");
    }

    public void clientToggleAutoReturn() {
        sendClientAction("toggleAutoReturn");
    }

    public void clientToggleWirelessStrategy() {
        sendClientAction("toggleWirelessStrategy");
    }

    // -- Client helpers --

    public boolean isWirelessMode() {
        return providerMode == ProviderMode.WIRELESS.ordinal();
    }

    public boolean isAutoReturnEnabled() {
        return autoReturn != 0;
    }

    public boolean isEvenDistribution() {
        return wirelessStrategy == WirelessStrategy.EVEN_DISTRIBUTION.ordinal();
    }
}
