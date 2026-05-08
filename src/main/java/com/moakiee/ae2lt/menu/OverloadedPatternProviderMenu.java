package com.moakiee.ae2lt.menu;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessSpeedMode;
import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;

public class OverloadedPatternProviderMenu extends PatternProviderMenu implements FrequencyBindingMenu {
    private static final ClientActionKey<Void> ACTION_TOGGLE_MODE = new ClientActionKey<>("toggleMode");
    private static final ClientActionKey<Void> ACTION_TOGGLE_AUTO_RETURN = new ClientActionKey<>("toggleAutoReturn");
    private static final ClientActionKey<Void> ACTION_TOGGLE_WIRELESS_DISPATCH_MODE =
            new ClientActionKey<>("toggleWirelessDispatchMode");
    private static final ClientActionKey<Void> ACTION_TOGGLE_WIRELESS_SPEED_MODE =
            new ClientActionKey<>("toggleWirelessSpeedMode");
    private static final ClientActionKey<Void> ACTION_TOGGLE_FILTERED_IMPORT =
            new ClientActionKey<>("toggleFilteredImport");
    private static final ClientActionKey<Void> ACTION_NEXT_PAGE = new ClientActionKey<>("nextPage");
    private static final ClientActionKey<Void> ACTION_PREV_PAGE = new ClientActionKey<>("prevPage");

    public static final MenuType<OverloadedPatternProviderMenu> TYPE = MenuTypeBuilder
            .create(OverloadedPatternProviderMenu::new, PatternProviderLogicHost.class)
            .buildUnregistered(Identifier.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_pattern_provider"));

    private static final int SLOTS_PER_PAGE = 36;

    @GuiSync(10)
    public int providerMode;

    @GuiSync(11)
    public int returnMode;

    @GuiSync(12)
    public int filteredImport;

    @GuiSync(13)
    public int wirelessDispatchMode;

    @GuiSync(16)
    public int wirelessSpeedMode;

    @GuiSync(14)
    public int currentPage;

    @GuiSync(15)
    public int totalPages;

    private final PatternProviderLogicHost host;
    private int lastShownPage = -1;

    public OverloadedPatternProviderMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        registerClientAction(ACTION_TOGGLE_MODE, this::toggleMode);
        registerClientAction(ACTION_TOGGLE_AUTO_RETURN, this::toggleAutoReturn);
        registerClientAction(ACTION_TOGGLE_WIRELESS_DISPATCH_MODE, this::toggleWirelessDispatchMode);
        registerClientAction(ACTION_TOGGLE_WIRELESS_SPEED_MODE, this::toggleWirelessSpeedMode);
        registerClientAction(ACTION_TOGGLE_FILTERED_IMPORT, this::toggleFilteredImport);
        registerClientAction(ACTION_NEXT_PAGE, this::nextPage);
        registerClientAction(ACTION_PREV_PAGE, this::prevPage);

        showPage(0);
        lastShownPage = -1;
    }

    public void showPage(int page) {
        if (page == lastShownPage) return;
        lastShownPage = page;

        List<net.minecraft.world.inventory.Slot> patternSlots =
                getSlots(SlotSemantics.ENCODED_PATTERN);

        int totalSlots = patternSlots.size();
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, totalSlots);

        for (int i = 0; i < totalSlots; i++) {
            var slot = patternSlots.get(i);
            if (slot instanceof AppEngSlot aeSlot) {
                aeSlot.setActive(i >= start && i < end);
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            providerMode = be.getProviderMode().ordinal();
            returnMode = be.getReturnMode().ordinal();
            filteredImport = be.isFilteredImport() ? 1 : 0;
            wirelessDispatchMode = be.getWirelessDispatchMode().ordinal();
            wirelessSpeedMode = be.getWirelessSpeedMode().ordinal();
            var logic = (OverloadedPatternProviderLogic) be.getLogic();
            currentPage = logic.getCurrentPage();
            totalPages = logic.getTotalPages();
            logic.syncReturnPageViewFromFull();
        }
        showPage(currentPage);
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
            var values = ReturnMode.values();
            var current = be.getReturnMode();
            be.setReturnMode(values[(current.ordinal() + 1) % values.length]);
        }
    }

    private void toggleWirelessDispatchMode() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var values = WirelessDispatchMode.values();
            var current = be.getWirelessDispatchMode();
            be.setWirelessDispatchMode(values[(current.ordinal() + 1) % values.length]);
        }
    }

    private void toggleWirelessSpeedMode() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var values = WirelessSpeedMode.values();
            var current = be.getWirelessSpeedMode();
            be.setWirelessSpeedMode(values[(current.ordinal() + 1) % values.length]);
        }
    }

    private void toggleFilteredImport() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            be.setFilteredImport(!be.isFilteredImport());
        }
    }

    private void nextPage() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var logic = (OverloadedPatternProviderLogic) be.getLogic();
            logic.setCurrentPage(logic.getCurrentPage() + 1);
        }
    }

    private void prevPage() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var logic = (OverloadedPatternProviderLogic) be.getLogic();
            logic.setCurrentPage(logic.getCurrentPage() - 1);
        }
    }

    // -- Public forwarding methods for Screen button callbacks --

    public void clientToggleMode() {
        sendClientAction(ACTION_TOGGLE_MODE);
    }

    public void clientToggleAutoReturn() {
        sendClientAction(ACTION_TOGGLE_AUTO_RETURN);
    }

    public void clientToggleWirelessDispatchMode() {
        sendClientAction(ACTION_TOGGLE_WIRELESS_DISPATCH_MODE);
    }

    public void clientToggleWirelessSpeedMode() {
        sendClientAction(ACTION_TOGGLE_WIRELESS_SPEED_MODE);
    }

    public void clientToggleFilteredImport() {
        sendClientAction(ACTION_TOGGLE_FILTERED_IMPORT);
    }

    // -- Client helpers --

    public boolean isWirelessMode() {
        return providerMode == ProviderMode.WIRELESS.ordinal();
    }

    public boolean isAutoReturnEnabled() {
        return returnMode != ReturnMode.OFF.ordinal();
    }

    public int getReturnModeOrdinal() {
        return returnMode;
    }

    public boolean isFilteredImport() {
        return filteredImport != 0;
    }

    public boolean isEvenDistributionMode() {
        return wirelessDispatchMode == WirelessDispatchMode.EVEN_DISTRIBUTION.ordinal();
    }

    public boolean isFastSpeedMode() {
        return wirelessSpeedMode == WirelessSpeedMode.FAST.ordinal();
    }

    public void clientNextPage() {
        sendClientAction(ACTION_NEXT_PAGE);
    }

    public void clientPrevPage() {
        sendClientAction(ACTION_PREV_PAGE);
    }

    @Override
    public net.minecraft.core.BlockPos getFrequencyBindingBlockPos() {
        if (host instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getBlockPos();
        }
        throw new IllegalStateException("Frequency binding host is not a block entity: " + host);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
