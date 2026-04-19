package com.moakiee.ae2lt.overload.armor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import appeng.menu.AEBaseMenu;

import de.mari_023.ae2wtlib.api.AE2wtlibAPI;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.OverloadArmorItem;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class OverloadArmorTerminalService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, ClientSession> CLIENT_SESSIONS = new HashMap<>();

    private OverloadArmorTerminalService() {
    }

    public static boolean openTerminal(ServerPlayer player, ItemStack armor) {
        UUID armorId = OverloadArmorState.ensureArmorId(armor);
        var carrier = OverloadArmorCarrierLocator.findEquipped(player, armorId);
        if (carrier == null) {
            return false;
        }

        var session = beginSession(player, carrier);
        if (session == null || !(session.liveTerminal.getItem() instanceof ItemWT itemWT)) {
            return false;
        }

        var opened = itemWT.tryOpen(player, session.asLocator(), false);
        if (!opened) {
            SESSIONS.remove(player.getUUID());
        } else {
            syncClientTerminal(player, session);
        }
        return opened;
    }

    public static boolean openEquippedTerminalFromHotkey(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        var carrier = OverloadArmorCarrierLocator.findFirstEquipped(
                player,
                stack -> stack.getItem() instanceof OverloadArmorItem
                        && OverloadArmorState.snapshot(
                                player,
                                stack,
                                player.level().registryAccess(),
                                true).canOpenTerminal());
        return carrier != null && openTerminal(serverPlayer, carrier.armorStack());
    }

    public static OverloadArmorTerminalLocator findTerminalLocator(Player player, WTDefinition terminalDefinition) {
        var carrier = OverloadArmorCarrierLocator.findFirstEquipped(
                player,
                stack -> matchesTerminalDefinition(player, stack, terminalDefinition));
        if (carrier == null) {
            return null;
        }

        return new OverloadArmorTerminalLocator(
                OverloadArmorState.ensureArmorId(carrier.armorStack()),
                carrier.locator(),
                OverloadArmorState.SLOT_TERMINAL,
                0L);
    }

    public static ItemStack getLiveTerminal(ServerPlayer player, OverloadArmorTerminalLocator locator) {
        var session = getValidSession(player, locator, locator.sessionVersion() != 0L);
        if (session == null && locator.sessionVersion() == 0L) {
            session = beginSession(player, locator);
        }
        return session != null ? session.liveTerminal : ItemStack.EMPTY;
    }

    public static ItemStack getClientLiveTerminal(Player player, OverloadArmorTerminalLocator locator) {
        var session = CLIENT_SESSIONS.get(player.getUUID());
        if (session != null && locator.matches(session.locator())) {
            return session.liveTerminal();
        }

        var carrier = locator.carrierLocator().resolve(player, locator.armorId());
        if (carrier == null) {
            CLIENT_SESSIONS.remove(player.getUUID());
            return ItemStack.EMPTY;
        }

        var terminal = OverloadArmorState.getSlot(
                carrier.armorStack(),
                player.level().registryAccess(),
                locator.terminalSlot());
        if (terminal.isEmpty()) {
            CLIENT_SESSIONS.remove(player.getUUID());
            return ItemStack.EMPTY;
        }

        var clientSession = new ClientSession(locator, terminal.copy());
        CLIENT_SESSIONS.put(player.getUUID(), clientSession);
        return clientSession.liveTerminal();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var existingSession = SESSIONS.get(player.getUUID());
        if (existingSession == null) {
            return;
        }

        var session = getValidSession(player, existingSession.asLocator(), true);
        if (session == null) {
            return;
        }

        syncClientTerminalIfNeeded(player, session);
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            scheduleFinishIfTerminalMenuClosed(player, event.getContainer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof Player player) {
            CLIENT_SESSIONS.remove(player.getUUID());
            if (player instanceof ServerPlayer serverPlayer) {
                finishSession(serverPlayer, true);
            }
        }
    }

    private static Session getValidSession(
            ServerPlayer player,
            OverloadArmorTerminalLocator locator,
            boolean closeContainerOnInvalid
    ) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !locator.matches(session.asLocator())) {
            return null;
        }

        var carrier = session.carrierLocator.resolve(player, session.armorId);
        if (carrier == null
                || !OverloadArmorState.matchesTerminalSession(
                        carrier.armorStack(),
                        session.sessionVersion,
                        session.contentVersion)
                || !OverloadArmorState.snapshot(
                        player,
                        carrier.armorStack(),
                        player.level().registryAccess(),
                        true).canOpenTerminal()) {
            finishSession(player, true);
            if (closeContainerOnInvalid) {
                player.closeContainer();
            }
            return null;
        }

        return session;
    }

    private static void finishSession(ServerPlayer player, boolean flushChanges) {
        var session = SESSIONS.remove(player.getUUID());
        if (session != null && flushChanges) {
            flush(player, session);
        }
    }

    private static void scheduleFinishIfTerminalMenuClosed(ServerPlayer player, AbstractContainerMenu closingMenu) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        if (!(closingMenu instanceof AEBaseMenu menu)
                || !(menu.getLocator() instanceof OverloadArmorTerminalLocator locator)
                || !locator.matches(session.asLocator())) {
            return;
        }

        var server = player.getServer();
        if (server == null) {
            finishSession(player, true);
            return;
        }

        server.execute(() -> {
            var currentSession = SESSIONS.get(player.getUUID());
            if (currentSession == null) {
                return;
            }

            if (player.containerMenu instanceof AEBaseMenu currentMenu
                    && currentMenu.getLocator() instanceof OverloadArmorTerminalLocator currentLocator
                    && currentLocator.matches(currentSession.asLocator())) {
                return;
            }

            finishSession(player, true);
        });
    }

    private static boolean flush(ServerPlayer player, Session session) {
        var carrier = session.carrierLocator.resolve(player, session.armorId);
        if (carrier == null) {
            return false;
        }

        boolean written = OverloadArmorState.writeTerminalForSession(
                carrier.armorStack(),
                player.level().registryAccess(),
                session.liveTerminal,
                session.sessionVersion,
                session.contentVersion);
        if (written) {
            carrier.commit(carrier.armorStack());
        }
        return written;
    }

    private static boolean matchesTerminalDefinition(Player player, ItemStack armor, WTDefinition terminalDefinition) {
        if (!(armor.getItem() instanceof OverloadArmorItem)) {
            return false;
        }

        if (!OverloadArmorState.snapshot(player, armor, player.level().registryAccess(), true).canOpenTerminal()) {
            return false;
        }

        var terminal = OverloadArmorState.getSlot(
                armor,
                player.level().registryAccess(),
                OverloadArmorState.SLOT_TERMINAL);
        return WUTHandler.hasTerminal(terminal, terminalDefinition);
    }

    private static Session beginSession(ServerPlayer player, OverloadArmorTerminalLocator locator) {
        var carrier = locator.carrierLocator().resolve(player, locator.armorId());
        if (carrier == null) {
            return null;
        }

        return beginSession(player, carrier);
    }

    private static Session beginSession(ServerPlayer player, OverloadArmorCarrierLocator.CarrierAccess carrier) {
        if (!OverloadArmorState.snapshot(player, carrier.armorStack(), player.level().registryAccess(), true).canOpenTerminal()) {
            return null;
        }

        var terminal = OverloadArmorState.getSlot(
                carrier.armorStack(),
                player.level().registryAccess(),
                OverloadArmorState.SLOT_TERMINAL);
        if (!(terminal.getItem() instanceof ItemWT)) {
            return null;
        }

        finishSession(player, true);
        var terminalSession = OverloadArmorState.beginTerminalSession(carrier.armorStack());
        var session = new Session(
                OverloadArmorState.ensureArmorId(carrier.armorStack()),
                carrier.locator(),
                terminalSession.sessionVersion(),
                terminalSession.contentVersion(),
                terminal.copy());
        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    private static void syncClientTerminalIfNeeded(ServerPlayer player, Session session) {
        if (ItemStack.matches(session.lastSyncedTerminal, session.liveTerminal)) {
            return;
        }

        syncClientTerminal(player, session);
    }

    private static void syncClientTerminal(ServerPlayer player, Session session) {
        AE2wtlibAPI.updateClientTerminal(player, session.asLocator(), session.liveTerminal);
        session.lastSyncedTerminal = session.liveTerminal.copy();
    }

    private static final class Session {
        private final UUID armorId;
        private final OverloadArmorCarrierLocator carrierLocator;
        private final long sessionVersion;
        private final long contentVersion;
        private final ItemStack liveTerminal;
        private ItemStack lastSyncedTerminal;

        private Session(
                UUID armorId,
                OverloadArmorCarrierLocator carrierLocator,
                long sessionVersion,
                long contentVersion,
                ItemStack liveTerminal
        ) {
            this.armorId = armorId;
            this.carrierLocator = carrierLocator;
            this.sessionVersion = sessionVersion;
            this.contentVersion = contentVersion;
            this.liveTerminal = liveTerminal;
            this.lastSyncedTerminal = ItemStack.EMPTY;
        }

        private OverloadArmorTerminalLocator asLocator() {
            return new OverloadArmorTerminalLocator(
                    armorId,
                    carrierLocator,
                    OverloadArmorState.SLOT_TERMINAL,
                    sessionVersion);
        }
    }

    private record ClientSession(OverloadArmorTerminalLocator locator, ItemStack liveTerminal) {
    }
}
