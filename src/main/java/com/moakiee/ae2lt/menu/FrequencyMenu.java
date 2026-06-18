package com.moakiee.ae2lt.menu;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuHostLocator;

import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardData;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.network.SyncFrequencyDetailPacket;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;

/**
 * Shared menu for wireless controllers, receiver-style devices and frequency
 * cards installed in AE2WTLib wireless terminals.
 */
public class FrequencyMenu extends AEBaseMenu implements ISubMenu {

    public static final MenuType<FrequencyMenu> TYPE = IMenuTypeExtension.create(FrequencyMenu::clientCreate);
    private static final ClientActionKey<Void> ACTION_TOGGLE_AUTO_CONNECT =
            new ClientActionKey<>("toggleAutoConnect");

    static {
        MenuOpener.addOpener(TYPE, FrequencyMenu::openWithAe2MenuOpener);
    }

    private final BlockPos blockPos;
    private final boolean isController;
    private final boolean isAdvanced;
    private final String deviceName;

    @Nullable
    private final BlockEntity backingBlockEntity;

    private final boolean cardMode;
    @Nullable
    private final ItemMenuHostLocator terminalLocator;
    @Nullable
    private final ServerPlayer cardPlayer;

    @Nullable
    private ISubMenuHost cachedSubMenuHost;
    private final boolean hasParentMenu;

    // DataSlot is still short-backed on the wire in 26.1; split int values.
    private final DataSlot freqIdLowSlot = DataSlot.standalone();
    private final DataSlot freqIdHighSlot = DataSlot.standalone();
    private final DataSlot linkActiveSlot = DataSlot.standalone();
    private final DataSlot usedChannelsLowSlot = DataSlot.standalone();
    private final DataSlot usedChannelsHighSlot = DataSlot.standalone();
    private final DataSlot maxChannelsLowSlot = DataSlot.standalone();
    private final DataSlot maxChannelsHighSlot = DataSlot.standalone();
    private final DataSlot autoConnectSlot = DataSlot.standalone();

    private static final int CHANNEL_REFRESH_INTERVAL = 10;
    private int channelRefreshCountdown = 0;

    public FrequencyMenu(int containerId, Inventory playerInv, BlockEntity be) {
        this(containerId, playerInv, be, null, null);
    }

    public FrequencyMenu(int containerId, Inventory playerInv, BlockEntity be,
                         @Nullable MenuType<?> parentType, @Nullable MenuHostLocator parentLocator) {
        super(TYPE, containerId, playerInv, be);
        this.blockPos = be.getBlockPos();
        this.backingBlockEntity = be;
        this.cardMode = false;
        this.terminalLocator = null;
        this.cardPlayer = null;

        if (parentType != null && parentLocator != null) {
            this.cachedSubMenuHost = new DeviceMenuReturnHost(
                    new ItemStack(be.getBlockState().getBlock()), parentType, parentLocator);
        }
        this.hasParentMenu = this.cachedSubMenuHost != null;

        if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            this.isController = true;
            this.isAdvanced = ctrl.isAdvanced();
            this.deviceName = ctrl.isAdvanced()
                    ? "block.ae2lt.advanced_wireless_overloaded_controller"
                    : "block.ae2lt.wireless_overloaded_controller";
            setSyncedFrequencyId(ctrl.getFrequencyId());
            this.linkActiveSlot.set(ctrl.isFrequencyActive() ? 1 : 0);
            setSyncedUsedChannels(ctrl.getGridUsedChannels());
            setSyncedMaxChannels(ctrl.getGridMaxChannels());
        } else if (be instanceof FrequencyBindingHost bindingHost) {
            this.isController = false;
            this.isAdvanced = false;
            this.deviceName = bindingHost.getFrequencyBindingDeviceName();
            setSyncedFrequencyId(bindingHost.getFrequencyId());
            this.linkActiveSlot.set(bindingHost.isFrequencyConnected() ? 1 : 0);
            setSyncedUsedChannels(bindingHost.getGridUsedChannels());
            setSyncedMaxChannels(bindingHost.getGridMaxChannels());
        } else {
            this.isController = false;
            this.isAdvanced = false;
            this.deviceName = "block.ae2lt.wireless_receiver";
            setSyncedFrequencyId(-1);
            this.linkActiveSlot.set(0);
            setSyncedUsedChannels(0);
            setSyncedMaxChannels(0);
        }
        this.autoConnectSlot.set(0);

        registerDataSlots();
        registerClientActions();

        if (playerInv.player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, SyncFrequencyListPacket.fromServer());
            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(sp, getCurrentFrequencyId());
            SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(sp, getCurrentFrequencyId());
        }
    }

    public FrequencyMenu(int containerId, Inventory playerInv, ItemMenuHostLocator terminalLocator) {
        super(TYPE, containerId, playerInv, null);
        setLocator(terminalLocator);
        this.blockPos = BlockPos.ZERO;
        this.backingBlockEntity = null;
        this.cardMode = true;
        this.terminalLocator = terminalLocator;
        this.cardPlayer = playerInv.player instanceof ServerPlayer sp ? sp : null;
        this.isController = false;
        this.isAdvanced = false;
        this.deviceName = "item.ae2lt.overloaded_frequency_card";

        this.cachedSubMenuHost = resolveSubMenuHost();
        this.hasParentMenu = this.cachedSubMenuHost != null;

        var cardData = readCardData();
        int freqId = cardData.isBound() ? cardData.frequencyId() : -1;
        setSyncedFrequencyId(freqId);
        this.linkActiveSlot.set(readCardLinkActive(freqId));
        setSyncedUsedChannels(0);
        setSyncedMaxChannels(0);
        this.autoConnectSlot.set(cardData.autoConnect() ? 1 : 0);

        registerDataSlots();
        registerClientActions();

        if (cardPlayer != null) {
            PacketDistributor.sendToPlayer(cardPlayer, SyncFrequencyListPacket.fromServer());
            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(cardPlayer, freqId);
            SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(cardPlayer, freqId);
        }
    }

    private static FrequencyMenu clientCreate(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        boolean cardMode = buf.readBoolean();
        BlockPos pos = buf.readBlockPos();
        boolean controller = buf.readBoolean();
        boolean advanced = buf.readBoolean();
        String deviceName = buf.readUtf(256);
        int freqId = buf.readInt();
        boolean linkActive = buf.readBoolean();
        int used = buf.readInt();
        int max = buf.readInt();
        boolean autoConnect = buf.readBoolean();
        boolean hasParentMenu = buf.readBoolean();
        return new FrequencyMenu(containerId, playerInv, cardMode, pos, controller, advanced, deviceName,
                freqId, linkActive, used, max, autoConnect, hasParentMenu);
    }

    private FrequencyMenu(int containerId, Inventory playerInv, boolean cardMode, BlockPos pos, boolean isController,
                          boolean isAdvanced, String deviceName, int freqId, boolean linkActive, int used, int max,
                          boolean autoConnect, boolean hasParentMenu) {
        super(TYPE, containerId, playerInv, null);
        this.blockPos = pos;
        this.isController = isController;
        this.isAdvanced = isAdvanced;
        this.deviceName = deviceName;
        this.backingBlockEntity = null;
        this.cardMode = cardMode;
        this.terminalLocator = null;
        this.cardPlayer = null;
        this.hasParentMenu = hasParentMenu;
        setSyncedFrequencyId(freqId);
        this.linkActiveSlot.set(linkActive ? 1 : 0);
        setSyncedUsedChannels(used);
        setSyncedMaxChannels(max);
        this.autoConnectSlot.set(autoConnect ? 1 : 0);
        registerDataSlots();
        registerClientActions();
    }

    private void registerDataSlots() {
        addDataSlot(freqIdLowSlot);
        addDataSlot(freqIdHighSlot);
        addDataSlot(linkActiveSlot);
        addDataSlot(usedChannelsLowSlot);
        addDataSlot(usedChannelsHighSlot);
        addDataSlot(maxChannelsLowSlot);
        addDataSlot(maxChannelsHighSlot);
        addDataSlot(autoConnectSlot);
    }

    private void registerClientActions() {
        registerClientAction(ACTION_TOGGLE_AUTO_CONNECT, this::toggleAutoConnect);
    }

    private static boolean openWithAe2MenuOpener(ServerPlayer serverPlayer, MenuHostLocator locator, boolean returning) {
        if (locator instanceof ItemMenuHostLocator terminalLocator) {
            ItemStack terminal = terminalLocator.locateItem(serverPlayer);
            if (!TerminalCardAccess.hasCard(terminal)) {
                return false;
            }
            serverPlayer.openMenu(new Ae2StyleFrequencyMenuProvider(
                    Component.translatable("item.ae2lt.overloaded_frequency_card"),
                    (id, inv) -> {
                        var menu = new FrequencyMenu(id, inv, terminalLocator);
                        menu.setLocator(terminalLocator);
                        menu.setReturnedFromSubScreen(returning);
                        return menu;
                    },
                    serverPlayer.containerMenu instanceof AEBaseMenu
            ), buf -> writeCardExtraData(buf, terminal));
            return true;
        }

        FrequencyBindingHost bindingHost = locator.locate(serverPlayer, FrequencyBindingHost.class);
        if (bindingHost == null) {
            return false;
        }
        BlockEntity be = bindingHost.getFrequencyBindingBlockEntity();

        MenuType<?> parentType = null;
        MenuHostLocator parentLocator = null;
        if (serverPlayer.containerMenu instanceof AEBaseMenu parentMenu) {
            parentType = parentMenu.getType();
            parentLocator = locator;
        }
        final MenuType<?> fParentType = parentType;
        final MenuHostLocator fParentLocator = parentLocator;
        final boolean hasParent = fParentType != null && fParentLocator != null;

        serverPlayer.openMenu(new Ae2StyleFrequencyMenuProvider(
                be.getBlockState().getBlock().getName(),
                (id, inv) -> {
                    var menu = new FrequencyMenu(id, inv, be, fParentType, fParentLocator);
                    menu.setLocator(locator);
                    menu.setReturnedFromSubScreen(returning);
                    return menu;
                },
                serverPlayer.containerMenu instanceof AEBaseMenu
        ), buf -> writeExtraData(buf, be, hasParent));
        return true;
    }

    @FunctionalInterface
    private interface FrequencyMenuFactory {
        FrequencyMenu create(int containerId, Inventory inventory);
    }

    private record Ae2StyleFrequencyMenuProvider(Component title, FrequencyMenuFactory factory,
                                                 boolean openedFromAe2Menu) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return title;
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return factory.create(containerId, playerInventory);
        }

        @Override
        public boolean shouldTriggerClientSideContainerClosingOnOpen() {
            return !openedFromAe2Menu;
        }
    }

    public static void writeExtraData(FriendlyByteBuf buf, BlockEntity be, boolean hasParentMenu) {
        buf.writeBoolean(false);
        buf.writeBlockPos(be.getBlockPos());
        if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            buf.writeBoolean(true);
            buf.writeBoolean(ctrl.isAdvanced());
            buf.writeUtf(ctrl.isAdvanced()
                    ? "block.ae2lt.advanced_wireless_overloaded_controller"
                    : "block.ae2lt.wireless_overloaded_controller", 256);
            buf.writeInt(ctrl.getFrequencyId());
            buf.writeBoolean(ctrl.isFrequencyActive());
            buf.writeInt(ctrl.getGridUsedChannels());
            buf.writeInt(ctrl.getGridMaxChannels());
        } else if (be instanceof FrequencyBindingHost bindingHost) {
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeUtf(bindingHost.getFrequencyBindingDeviceName(), 256);
            buf.writeInt(bindingHost.getFrequencyId());
            buf.writeBoolean(bindingHost.isFrequencyConnected());
            buf.writeInt(bindingHost.getGridUsedChannels());
            buf.writeInt(bindingHost.getGridMaxChannels());
        } else {
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeUtf("block.ae2lt.wireless_receiver", 256);
            buf.writeInt(-1);
            buf.writeBoolean(false);
            buf.writeInt(0);
            buf.writeInt(0);
        }
        buf.writeBoolean(false);
        buf.writeBoolean(hasParentMenu);
    }

    public static void writeExtraData(FriendlyByteBuf buf, BlockEntity be) {
        writeExtraData(buf, be, false);
    }

    public static void writeCardExtraData(FriendlyByteBuf buf, ItemStack terminalStack) {
        var cardData = TerminalCardAccess.readCardData(terminalStack);
        int freqId = cardData.isBound() ? cardData.frequencyId() : -1;
        buf.writeBoolean(true);
        buf.writeBlockPos(BlockPos.ZERO);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeUtf("item.ae2lt.overloaded_frequency_card", 256);
        buf.writeInt(freqId);
        buf.writeBoolean(false);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeBoolean(cardData.autoConnect());
        buf.writeBoolean(true);
    }

    @Override
    public void broadcastChanges() {
        if (cardMode) {
            var cardData = readCardData();
            int real = cardData.isBound() ? cardData.frequencyId() : -1;
            if (getCurrentFrequencyId() != real) {
                setSyncedFrequencyId(real);
                if (cardPlayer != null && cardPlayer.containerMenu == this) {
                    SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(cardPlayer, real);
                    SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(cardPlayer, real);
                }
            }
            int autoConnect = cardData.autoConnect() ? 1 : 0;
            if (autoConnectSlot.get() != autoConnect) {
                autoConnectSlot.set(autoConnect);
            }
            if (--channelRefreshCountdown <= 0) {
                channelRefreshCountdown = CHANNEL_REFRESH_INTERVAL;
                int active = readCardLinkActive(real);
                if (linkActiveSlot.get() != active) {
                    linkActiveSlot.set(active);
                }
            }
            super.broadcastChanges();
            return;
        }

        if (backingBlockEntity != null) {
            int real = readFreqIdFromBE();
            if (getCurrentFrequencyId() != real) {
                setSyncedFrequencyId(real);
                var lvl = backingBlockEntity.getLevel();
                if (lvl != null && !lvl.isClientSide()) {
                    for (var p : lvl.players()) {
                        if (p instanceof ServerPlayer sp && sp.containerMenu == this) {
                            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(sp, real);
                            SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(sp, real);
                        }
                    }
                }
            }

            int active = readLinkActiveFromBE();
            if (linkActiveSlot.get() != active) {
                linkActiveSlot.set(active);
            }

            if (--channelRefreshCountdown <= 0) {
                channelRefreshCountdown = CHANNEL_REFRESH_INTERVAL;
                int used = readUsedChannelsFromBE();
                if (getUsedChannels() != used) {
                    setSyncedUsedChannels(used);
                }
                int max = readMaxChannelsFromBE();
                if (getMaxChannels() != max) {
                    setSyncedMaxChannels(max);
                }
            }
        }
        super.broadcastChanges();
    }

    private int readFreqIdFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getFrequencyId();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getFrequencyId();
        }
        return -1;
    }

    private int readLinkActiveFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.isFrequencyActive() ? 1 : 0;
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.isFrequencyConnected() ? 1 : 0;
        }
        return 0;
    }

    private int readUsedChannelsFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getGridUsedChannels();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getGridUsedChannels();
        }
        return 0;
    }

    private int readMaxChannelsFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getGridMaxChannels();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getGridMaxChannels();
        }
        return 0;
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        if (cardMode) {
            return TerminalCardAccess.hasCard(resolveTerminalStack());
        }
        if (backingBlockEntity != null) {
            if (backingBlockEntity.isRemoved() || backingBlockEntity.getLevel() == null) {
                return false;
            }

            if (player.level() != backingBlockEntity.getLevel()) {
                return false;
            }

            if (backingBlockEntity.getLevel().getBlockEntity(blockPos) != backingBlockEntity) {
                return false;
            }
        }

        return player.distanceToSqr(
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        return ItemStack.EMPTY;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public boolean isController() {
        return isController;
    }

    public boolean isAdvanced() {
        return isAdvanced;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public int getCurrentFrequencyId() {
        return readSplitInt(freqIdLowSlot, freqIdHighSlot);
    }

    public boolean isCardMode() {
        return cardMode;
    }

    public boolean hasParentMenu() {
        return hasParentMenu;
    }

    public boolean isAutoConnect() {
        return autoConnectSlot.get() != 0;
    }

    public void clientToggleAutoConnect() {
        sendClientAction(ACTION_TOGGLE_AUTO_CONNECT);
    }

    public boolean isLinkActive() {
        return linkActiveSlot.get() != 0;
    }

    public ItemStack resolveTerminalStack() {
        if (terminalLocator == null || cardPlayer == null) {
            return ItemStack.EMPTY;
        }
        return terminalLocator.locateItem(cardPlayer);
    }

    @Override
    @Nullable
    public ISubMenuHost getHost() {
        return cachedSubMenuHost;
    }

    @Nullable
    private ISubMenuHost resolveSubMenuHost() {
        if (terminalLocator == null || cardPlayer == null) {
            return null;
        }
        ItemStack terminal = resolveTerminalStack();
        if (terminal.getItem() instanceof IMenuItem menuItem
                && menuItem.getMenuHost(cardPlayer, terminalLocator, (BlockHitResult) null)
                        instanceof ISubMenuHost subHost) {
            return subHost;
        }
        return null;
    }

    private record DeviceMenuReturnHost(ItemStack icon, MenuType<?> parentType, MenuHostLocator parentLocator)
            implements ISubMenuHost {
        @Override
        public ItemStack getMainMenuIcon() {
            return icon;
        }

        @Override
        public void returnToMainMenu(Player player, ISubMenu subMenu) {
            MenuOpener.open(parentType, player, parentLocator);
        }
    }

    private OverloadedFrequencyCardData readCardData() {
        return TerminalCardAccess.readCardData(resolveTerminalStack());
    }

    private void toggleAutoConnect() {
        if (!cardMode || !isServerSide() || cardPlayer == null || !stillValid(cardPlayer)) {
            return;
        }
        var cardData = readCardData();
        if (cardData.isBound() && !cardData.canBeUsedBy(cardPlayer.getUUID())) {
            return;
        }
        if (TerminalCardAccess.updateCard(resolveTerminalStack(), OverloadedFrequencyCardData::toggleAutoConnect)) {
            autoConnectSlot.set(readCardData().autoConnect() ? 1 : 0);
            broadcastChanges();
        }
    }

    private int readCardLinkActive(int freqId) {
        if (freqId <= 0 || cardPlayer == null) {
            return 0;
        }
        var manager = WirelessFrequencyManager.get();
        if (manager == null) {
            return 0;
        }
        return manager.resolveAdvancedNode(freqId, cardPlayer.level().getServer()) != null ? 1 : 0;
    }

    public int getUsedChannels() {
        return readSplitInt(usedChannelsLowSlot, usedChannelsHighSlot);
    }

    public int getMaxChannels() {
        return readSplitInt(maxChannelsLowSlot, maxChannelsHighSlot);
    }

    private void setSyncedFrequencyId(int value) {
        writeSplitInt(freqIdLowSlot, freqIdHighSlot, value);
    }

    private void setSyncedUsedChannels(int value) {
        writeSplitInt(usedChannelsLowSlot, usedChannelsHighSlot, value);
    }

    private void setSyncedMaxChannels(int value) {
        writeSplitInt(maxChannelsLowSlot, maxChannelsHighSlot, value);
    }

    private static void writeSplitInt(DataSlot low, DataSlot high, int value) {
        low.set(value & 0xFFFF);
        high.set((value >>> 16) & 0xFFFF);
    }

    private static int readSplitInt(DataSlot low, DataSlot high) {
        return ((high.get() & 0xFFFF) << 16) | (low.get() & 0xFFFF);
    }

    @Nullable
    public static FrequencyMenu validateToken(ServerPlayer player, int token) {
        if (player.containerMenu instanceof FrequencyMenu fm && fm.containerId == token) {
            return fm.stillValid(player) ? fm : null;
        }
        return null;
    }
}
