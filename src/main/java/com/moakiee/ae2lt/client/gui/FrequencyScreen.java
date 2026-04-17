package com.moakiee.ae2lt.client.gui;

import java.util.UUID;

import com.moakiee.ae2lt.client.ClientFrequencyCache;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Multi-tab frequency management screen, modeled after Flux Networks' GUI.
 * Polls {@link ClientFrequencyCache#revision()} each frame and rebuilds
 * widgets when the server-pushed state changes.
 */
public class FrequencyScreen extends AbstractContainerScreen<FrequencyMenu> {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_WIDTH = 18;
    private static final int ITEMS_PER_PAGE = 6;

    private FrequencyNavigationTab currentTab = FrequencyNavigationTab.TAB_HOME;
    private int selectionPage = 0;
    private int memberPage = 0;
    private int connectionPage = 0;

    private EditBox nameField;
    private EditBox passwordField;
    private String selectionPassword = "";
    private FrequencySecurityLevel editSecurity = FrequencySecurityLevel.PRIVATE;
    private int editColor = 0x1E90FF;

    private int lastCacheRevision = -1;
    private int lastFreqId = Integer.MIN_VALUE;

    // popup state for Members tab
    private UUID popupMemberUUID;
    private String popupMemberName = "";
    private FrequencyAccessLevel popupMemberAccess = FrequencyAccessLevel.USER;

    private static final int[] PRESET_COLORS = {
            0x6B17E8, 0x4400EC, 0x0033FF, 0x00ABFF, 0x00FFD9, 0x00FF00, 0x77FF00,
            0xFFFF00, 0xFF8800, 0xFF0000, 0xFF006A, 0xEC00EC, 0x7F7F7F, 0xFFFFFF
    };

    private FrequencyMenu freqMenu() { return getMenu(); }

    private int token() { return getMenu().containerId; }

    public FrequencyScreen(FrequencyMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        lastCacheRevision = ClientFrequencyCache.revision();
        lastFreqId = freqMenu().getCurrentFrequencyId();
        initTabWidgets();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        int rev = ClientFrequencyCache.revision();
        int fid = freqMenu().getCurrentFrequencyId();
        if (rev != lastCacheRevision || fid != lastFreqId) {
            lastCacheRevision = rev;
            lastFreqId = fid;
            initTabWidgets();
        }
    }

    private void initTabWidgets() {
        clearWidgets();
        closePopup();

        int x0 = leftPos;
        int y0 = topPos;

        for (int i = 0; i < FrequencyNavigationTab.VALUES.length; i++) {
            FrequencyNavigationTab tab = FrequencyNavigationTab.VALUES[i];
            int bx = (tab == FrequencyNavigationTab.TAB_CREATE)
                    ? x0 + GUI_WIDTH - TAB_WIDTH - 4
                    : x0 + 8 + i * (TAB_WIDTH + 2);
            int by = y0 - TAB_HEIGHT + 2;
            var button = Button.builder(
                            Component.literal(tabIcon(tab)),
                            btn -> switchTab(tab))
                    .bounds(bx, by, TAB_WIDTH + 8, TAB_HEIGHT - 2)
                    .tooltip(Tooltip.create(Component.translatable(tab.getTranslationKey())))
                    .build();
            button.active = true;
            addRenderableWidget(button);
        }

        switch (currentTab) {
            case TAB_HOME -> initHomeTab(x0, y0);
            case TAB_SELECTION -> initSelectionTab(x0, y0);
            case TAB_CONNECTION -> initConnectionsTab(x0, y0);
            case TAB_MEMBER -> initMembersTab(x0, y0);
            case TAB_CREATE -> initCreateTab(x0, y0);
            case TAB_SETTING -> initSettingsTab(x0, y0);
        }
    }

    private void switchTab(FrequencyNavigationTab tab) {
        currentTab = tab;
        selectionPage = 0;
        memberPage = 0;
        connectionPage = 0;
        selectionPassword = "";
        initTabWidgets();
    }

    // Tab: Home

    private void initHomeTab(int x0, int y0) {
        addRenderableWidget(Button.builder(
                        Component.translatable("ae2lt.gui.button.disconnect"),
                        btn -> PacketDistributor.sendToServer(
                                new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), -1, "")))
                .bounds(x0 + 10, y0 + 130, 80, 16)
                .build());
    }

    // Tab: Selection

    private void initSelectionTab(int x0, int y0) {
        passwordField = new EditBox(font, x0 + 14, y0 + 128, 98, 14,
                Component.translatable("ae2lt.gui.frequency.password"));
        passwordField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordField.setValue(selectionPassword);
        passwordField.setResponder(value -> selectionPassword = value);
        addRenderableWidget(passwordField);

        var freqs = ClientFrequencyCache.getAllFrequenciesSorted();
        int totalPages = Math.max(1, (freqs.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        selectionPage = Math.min(selectionPage, totalPages - 1);

        int start = selectionPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, freqs.size());

        for (int i = start; i < end; i++) {
            var f = freqs.get(i);
            int row = i - start;
            int by = y0 + 30 + row * 16;

            String label = switch (f.security()) {
                case ENCRYPTED -> f.name() + " [E]";
                case PRIVATE -> f.name() + " [P]";
                default -> f.name();
            };
            addRenderableWidget(Button.builder(
                            Component.literal(label),
                            b -> PacketDistributor.sendToServer(
                                    new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), f.id(), selectionPassword)))
                    .bounds(x0 + 14, by, 148, 14)
                    .build());
        }

        if (selectionPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("<<"),
                            btn -> { selectionPage--; initTabWidgets(); })
                    .bounds(x0 + 14, y0 + 148, 30, 14).build());
        }
        if (selectionPage < totalPages - 1) {
            addRenderableWidget(Button.builder(Component.literal(">>"),
                            btn -> { selectionPage++; initTabWidgets(); })
                    .bounds(x0 + 132, y0 + 148, 30, 14).build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("ae2lt.gui.button.disconnect"),
                        btn -> PacketDistributor.sendToServer(
                                new SelectFrequencyPacket(token(), freqMenu().getBlockPos(), -1, "")))
                .bounds(x0 + 118, y0 + 128, 44, 14).build());
    }

    // Tab: Connections

    private void initConnectionsTab(int x0, int y0) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) return;

        var conns = ClientFrequencyCache.getConnections(currentId);
        int totalPages = Math.max(1, (conns.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        connectionPage = Math.min(connectionPage, totalPages - 1);

        if (connectionPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("<<"),
                            btn -> { connectionPage--; initTabWidgets(); })
                    .bounds(x0 + 14, y0 + 148, 30, 14).build());
        }
        if (connectionPage < totalPages - 1) {
            addRenderableWidget(Button.builder(Component.literal(">>"),
                            btn -> { connectionPage++; initTabWidgets(); })
                    .bounds(x0 + 132, y0 + 148, 30, 14).build());
        }
    }

    // Tab: Members

    private void initMembersTab(int x0, int y0) {        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) return;

        var members = ClientFrequencyCache.getMembers(currentId);
        int totalPages = Math.max(1, (members.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        memberPage = Math.min(memberPage, totalPages - 1);

        int start = memberPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, members.size());

        for (int i = start; i < end; i++) {
            var m = members.get(i);
            int row = i - start;
            int by = y0 + 30 + row * 16;

            String label = m.name();
            if (isSelf(m.uuid())) {
                label += " " + Component.translatable("ae2lt.gui.member.you").getString();
            }
            String suffix = " [" + accessShort(m.access()) + "]";
            Component display = Component.literal(label).withStyle(m.access().getFormatting())
                    .copy().append(Component.literal(suffix).withStyle(ChatFormatting.GRAY));
            addRenderableWidget(Button.builder(display,
                            b -> openMemberPopup(m.uuid(), m.name(), m.access()))
                    .bounds(x0 + 14, by, 148, 14)
                    .build());
        }

        if (memberPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("<<"),
                            btn -> { memberPage--; initTabWidgets(); })
                    .bounds(x0 + 14, y0 + 148, 30, 14).build());
        }
        if (memberPage < totalPages - 1) {
            addRenderableWidget(Button.builder(Component.literal(">>"),
                            btn -> { memberPage++; initTabWidgets(); })
                    .bounds(x0 + 132, y0 + 148, 30, 14).build());
        }
    }

    // Member edit popup

    private void openMemberPopup(UUID uuid, String name, FrequencyAccessLevel access) {
        popupMemberUUID = uuid;
        popupMemberName = name;
        popupMemberAccess = access;
        rebuildMemberPopupWidgets();
    }

    private void closePopup() {
        popupMemberUUID = null;
    }

    private void rebuildMemberPopupWidgets() {
        // re-init widgets with popup overlay visible
        clearWidgets();
        // keep nav buttons active
        int x0 = leftPos;
        int y0 = topPos;
        for (int i = 0; i < FrequencyNavigationTab.VALUES.length; i++) {
            FrequencyNavigationTab tab = FrequencyNavigationTab.VALUES[i];
            int bx = (tab == FrequencyNavigationTab.TAB_CREATE)
                    ? x0 + GUI_WIDTH - TAB_WIDTH - 4
                    : x0 + 8 + i * (TAB_WIDTH + 2);
            int by = y0 - TAB_HEIGHT + 2;
            var button = Button.builder(
                            Component.literal(tabIcon(tab)),
                            btn -> { closePopup(); switchTab(tab); })
                    .bounds(bx, by, TAB_WIDTH + 8, TAB_HEIGHT - 2)
                    .tooltip(Tooltip.create(Component.translatable(tab.getTranslationKey())))
                    .build();
            button.active = true;
            addRenderableWidget(button);
        }

        int px = x0 + 20;
        int py = y0 + 40;
        int pw = 136;

        boolean canEdit = hasEditAccess();
        boolean canDelete = hasOwnerAccess();
        boolean targetProtected = popupMemberAccess == FrequencyAccessLevel.OWNER;

        // set user
        var btnUser = Button.builder(
                        Component.translatable("ae2lt.gui.member.set_user"),
                        btn -> sendMember(WirelessFrequency.MEMBERSHIP_SET_USER))
                .bounds(px, py, pw, 14).build();
        btnUser.active = canEdit && !targetProtected && popupMemberAccess != FrequencyAccessLevel.USER;
        addRenderableWidget(btnUser);

        // set admin (owner only)
        var btnAdmin = Button.builder(
                        Component.translatable("ae2lt.gui.member.set_admin"),
                        btn -> sendMember(WirelessFrequency.MEMBERSHIP_SET_ADMIN))
                .bounds(px, py + 18, pw, 14).build();
        btnAdmin.active = canDelete && !targetProtected && popupMemberAccess != FrequencyAccessLevel.ADMIN;
        addRenderableWidget(btnAdmin);

        // remove
        var btnRemove = Button.builder(
                        Component.translatable("ae2lt.gui.member.remove")
                                .copy().withStyle(ChatFormatting.RED),
                        btn -> sendMember(WirelessFrequency.MEMBERSHIP_CANCEL))
                .bounds(px, py + 36, pw, 14).build();
        btnRemove.active = canEdit && !targetProtected;
        addRenderableWidget(btnRemove);

        // transfer ownership
        var btnTransfer = Button.builder(
                        Component.translatable("ae2lt.gui.member.transfer")
                                .copy().withStyle(ChatFormatting.GOLD),
                        btn -> sendMember(WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP))
                .bounds(px, py + 54, pw, 14).build();
        btnTransfer.active = canDelete && !isSelf(popupMemberUUID) && !targetProtected;
        addRenderableWidget(btnTransfer);

        // cancel
        addRenderableWidget(Button.builder(
                        Component.translatable("ae2lt.gui.button.cancel"),
                        btn -> { closePopup(); initTabWidgets(); })
                .bounds(px + 36, py + 78, 64, 14).build());
    }

    private void sendMember(byte type) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0 || popupMemberUUID == null) return;
        PacketDistributor.sendToServer(new ChangeMemberPacket(token(), currentId, popupMemberUUID, type));
        closePopup();
        initTabWidgets();
    }

    // Tab: Create

    private void initCreateTab(int x0, int y0) {
        nameField = new EditBox(font, x0 + 16, y0 + 30, 144, 14,
                Component.translatable("ae2lt.gui.frequency.name"));
        nameField.setMaxLength(WirelessFrequency.MAX_NAME_LENGTH);
        addRenderableWidget(nameField);

        passwordField = new EditBox(font, x0 + 16, y0 + 68, 144, 14,
                Component.translatable("ae2lt.gui.frequency.password"));
        passwordField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
        addRenderableWidget(passwordField);

        addRenderableWidget(Button.builder(
                        getSecurityLabel(editSecurity),
                        btn -> {
                            editSecurity = FrequencySecurityLevel.VALUES[
                                    (editSecurity.ordinal() + 1) % FrequencySecurityLevel.VALUES.length];
                            passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
                            btn.setMessage(getSecurityLabel(editSecurity));
                        })
                .bounds(x0 + 70, y0 + 50, 80, 14).build());

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int c = PRESET_COLORS[i];
            int cx = x0 + 48 + (i % 7) * 16;
            int cy = y0 + 90 + (i / 7) * 16;
            addRenderableWidget(Button.builder(Component.literal(" "),
                            btn -> editColor = c)
                    .bounds(cx, cy, 14, 14).build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("ae2lt.gui.button.create"),
                        btn -> {
                            if (nameField.getValue().isBlank()) return;
                            PacketDistributor.sendToServer(new CreateFrequencyPacket(
                                    token(),
                                    nameField.getValue(), editColor, editSecurity,
                                    passwordField.getValue()));
                            switchTab(FrequencyNavigationTab.TAB_SELECTION);
                        })
                .bounds(x0 + 64, y0 + 140, 48, 16).build());
    }

    // Tab: Settings

    private void initSettingsTab(int x0, int y0) {
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        if (freq == null) return;

        nameField = new EditBox(font, x0 + 16, y0 + 30, 144, 14,
                Component.translatable("ae2lt.gui.frequency.name"));
        nameField.setMaxLength(WirelessFrequency.MAX_NAME_LENGTH);
        nameField.setValue(freq.name());
        addRenderableWidget(nameField);

        passwordField = new EditBox(font, x0 + 16, y0 + 68, 144, 14,
                Component.translatable("ae2lt.gui.frequency.password"));
        passwordField.setMaxLength(WirelessFrequency.MAX_PASSWORD_LENGTH);
        addRenderableWidget(passwordField);

        editSecurity = freq.security();
        editColor = freq.color();

        addRenderableWidget(Button.builder(
                        getSecurityLabel(editSecurity),
                        btn -> {
                            editSecurity = FrequencySecurityLevel.VALUES[
                                    (editSecurity.ordinal() + 1) % FrequencySecurityLevel.VALUES.length];
                            passwordField.setVisible(editSecurity == FrequencySecurityLevel.ENCRYPTED);
                            btn.setMessage(getSecurityLabel(editSecurity));
                        })
                .bounds(x0 + 70, y0 + 50, 80, 14).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("ae2lt.gui.button.apply"),
                        btn -> PacketDistributor.sendToServer(new EditFrequencyPacket(
                                token(),
                                freq.id(), nameField.getValue(), editColor,
                                editSecurity, passwordField.getValue())))
                .bounds(x0 + 96, y0 + 140, 48, 16).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("ae2lt.gui.button.delete")
                                .copy().withStyle(ChatFormatting.RED),
                        btn -> {
                            PacketDistributor.sendToServer(new DeleteFrequencyPacket(token(), freq.id()));
                            switchTab(FrequencyNavigationTab.TAB_SELECTION);
                        })
                .bounds(x0 + 32, y0 + 140, 48, 16).build());
    }

    // Rendering

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xCC080808);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF404040);

        for (int i = 0; i < FrequencyNavigationTab.VALUES.length; i++) {
            if (FrequencyNavigationTab.VALUES[i] == currentTab) {
                int bx = (currentTab == FrequencyNavigationTab.TAB_CREATE)
                        ? leftPos + GUI_WIDTH - TAB_WIDTH - 4
                        : leftPos + 8 + i * (TAB_WIDTH + 2);
                g.fill(bx, topPos - 2, bx + TAB_WIDTH + 8, topPos, 0xFF00AAFF);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font,
                Component.translatable(currentTab.getTranslationKey()),
                imageWidth / 2, 6, 0xB4B4B4);

        switch (currentTab) {
            case TAB_HOME -> renderHomeLabels(g);
            case TAB_SELECTION -> renderSelectionLabels(g);
            case TAB_CONNECTION -> renderConnectionLabels(g);
            case TAB_MEMBER -> renderMemberLabels(g);
            case TAB_CREATE -> renderCreateLabels(g);
            case TAB_SETTING -> renderSettingLabels(g);
        }

        if (popupMemberUUID != null) {
            g.fill(leftPos + 10, topPos + 26, leftPos + 166, topPos + 136, 0xEE101010);
            g.renderOutline(leftPos + 10, topPos + 26, 156, 110, 0xFF00AAFF);
            g.drawString(font, Component.literal(popupMemberName)
                            .withStyle(popupMemberAccess.getFormatting()),
                    20, 30, 0xFFFFFF);
            g.drawString(font, Component.literal("[" + accessShort(popupMemberAccess) + "]")
                            .withStyle(ChatFormatting.GRAY), 20, 42, 0xAAAAAA);
        }
    }

    private void renderHomeLabels(GuiGraphics g) {
        int y = 22;
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        if (freq != null) {
            g.drawString(font, Component.translatable("ae2lt.gui.frequency.current")
                    .append(": " + ChatFormatting.AQUA + freq.name() + " (#" + freq.id() + ")"), 8, y, 0xFFFFFF);
        } else {
            g.drawString(font, Component.translatable("ae2lt.gui.frequency.none")
                    .withStyle(ChatFormatting.GRAY), 8, y, 0x808080);
        }
        y += 16;

        String deviceType = freqMenu().isController()
                ? (freqMenu().isAdvanced()
                        ? "block.ae2lt.advanced_wireless_overloaded_controller"
                        : "block.ae2lt.wireless_overloaded_controller")
                : "block.ae2lt.wireless_receiver";
        g.drawString(font, Component.translatable("ae2lt.gui.home.device_type")
                .append(": ").append(Component.translatable(deviceType)), 8, y, 0xB0B0B0);
        y += 12;

        boolean connected = freqMenu().isLinkActive();
        g.drawString(font, Component.translatable("ae2lt.gui.home.status")
                        .append(": ")
                        .append(connected
                                ? Component.translatable("ae2lt.gui.home.connected").withStyle(ChatFormatting.GREEN)
                                : Component.translatable("ae2lt.gui.home.disconnected").withStyle(ChatFormatting.RED)),
                8, y, 0xB0B0B0);
        y += 12;

        int used = freqMenu().getUsedChannels();
        int max = freqMenu().getMaxChannels();
        Component channelsValue;
        ChatFormatting color;
        if (max < 0) {
            channelsValue = Component.translatable("ae2lt.gui.home.grid_channels.infinite", used);
            color = ChatFormatting.AQUA;
        } else if (max == 0) {
            channelsValue = Component.translatable("ae2lt.gui.home.grid_channels.value", used, 0, 0);
            color = ChatFormatting.GRAY;
        } else {
            int remain = Math.max(0, max - used);
            channelsValue = Component.translatable("ae2lt.gui.home.grid_channels.value", used, max, remain);
            color = remain == 0 ? ChatFormatting.RED
                    : (remain * 4 < max ? ChatFormatting.GOLD : ChatFormatting.GREEN);
        }
        g.drawString(font, Component.translatable("ae2lt.gui.home.grid_channels")
                        .append(": ")
                        .append(channelsValue.copy().withStyle(color)),
                8, y, 0xB0B0B0);
        y += 12;

        if (freqMenu().isController()) {
            g.drawString(font, Component.translatable("ae2lt.gui.home.cross_dimension")
                    .append(": ").append(freqMenu().isAdvanced()
                            ? Component.translatable("ae2lt.gui.home.cross_dimension.yes").withStyle(ChatFormatting.GREEN)
                            : Component.translatable("ae2lt.gui.home.cross_dimension.no").withStyle(ChatFormatting.RED)),
                    8, y, 0xB0B0B0);
        }
    }

    private void renderSelectionLabels(GuiGraphics g) {
        var freqs = ClientFrequencyCache.getAllFrequenciesSorted();
        var current = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());

        if (current != null) {
            g.drawString(font, Component.translatable("ae2lt.gui.frequency.current")
                    .append(": " + ChatFormatting.AQUA + current.name()), 8, 12, 0xFFFFFF);
        }
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.total")
                .append(": " + freqs.size()), 8, 22, 0x808080);
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.password").append(":"), 8, 118, 0x808080);
    }

    private void renderConnectionLabels(GuiGraphics g) {
        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) {
            g.drawCenteredString(font, Component.translatable("ae2lt.gui.error.no_frequency")
                    .withStyle(ChatFormatting.GRAY), imageWidth / 2, 40, 0x808080);
            return;
        }

        var conns = ClientFrequencyCache.getConnections(currentId);
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.total")
                .append(": " + conns.size()), 8, 12, 0x808080);

        if (conns.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("ae2lt.gui.connection.none")
                    .withStyle(ChatFormatting.GRAY), imageWidth / 2, 70, 0x808080);
            return;
        }

        int start = connectionPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, conns.size());
        for (int i = start; i < end; i++) {
            var c = conns.get(i);
            int row = i - start;
            int y = 30 + row * 16;

            String typeKey = c.controller()
                    ? (c.advanced()
                            ? "block.ae2lt.advanced_wireless_overloaded_controller"
                            : "block.ae2lt.wireless_overloaded_controller")
                    : "block.ae2lt.wireless_receiver";
            ChatFormatting typeColor = c.controller() ? ChatFormatting.AQUA : ChatFormatting.GREEN;

            // type
            g.drawString(font, Component.translatable(typeKey).withStyle(typeColor), 8, y, 0xFFFFFF);

            // position
            String posStr = "(" + c.pos().getX() + "," + c.pos().getY() + "," + c.pos().getZ() + ")";
            g.drawString(font, Component.literal(posStr).withStyle(ChatFormatting.GRAY), 8, y + 8, 0x808080);

            // loaded indicator dot + short dim
            int dotColor = c.loaded() ? 0xFF55FF55 : 0xFF888888;
            g.fill(158, y + 2, 164, y + 8, dotColor);

            String dim = c.dimension();
            int slash = dim.indexOf(':');
            String shortDim = slash >= 0 ? dim.substring(slash + 1) : dim;
            if (shortDim.length() > 8) shortDim = shortDim.substring(0, 8);
            g.drawString(font, Component.literal(shortDim).withStyle(ChatFormatting.DARK_GRAY),
                    imageWidth - 4 - font.width(shortDim), y + 8, 0x606060);
        }
    }

    private void renderMemberLabels(GuiGraphics g) {        int currentId = freqMenu().getCurrentFrequencyId();
        if (currentId <= 0) {
            g.drawCenteredString(font, Component.translatable("ae2lt.gui.error.no_frequency")
                    .withStyle(ChatFormatting.GRAY), imageWidth / 2, 40, 0x808080);
            return;
        }

        FrequencyAccessLevel myAccess = selfAccess();
        g.drawString(font, Component.translatable("ae2lt.gui.member.your_access",
                        Component.translatable("ae2lt.gui.access." + myAccess.name().toLowerCase())
                                .withStyle(myAccess.getFormatting())), 8, 12, 0xB0B0B0);

        var members = ClientFrequencyCache.getMembers(currentId);
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.total")
                .append(": " + members.size()), 8, 22, 0x808080);

        if (members.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("ae2lt.gui.member.none")
                    .withStyle(ChatFormatting.GRAY), imageWidth / 2, 70, 0x808080);
        } else {
            g.drawString(font, Component.translatable("ae2lt.gui.member.select_hint")
                    .withStyle(ChatFormatting.DARK_GRAY), 8, 120, 0x606060);
        }
    }

    private void renderCreateLabels(GuiGraphics g) {
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.name").append(":"), 16, 22, 0x808080);
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.security").append(": "), 16, 52, 0x808080);
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.color").append(":"), 16, 82, 0x808080);

        g.fill(16, 126, 28, 138, editColor | 0xFF000000);
        if (nameField != null && !nameField.getValue().isBlank()) {
            g.drawString(font, nameField.getValue(), 32, 128, editColor | 0xFF000000);
        }
    }

    private void renderSettingLabels(GuiGraphics g) {
        if (ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId()) == null) {
            g.drawCenteredString(font, Component.translatable("ae2lt.gui.error.no_frequency")
                    .withStyle(ChatFormatting.GRAY), imageWidth / 2, 40, 0x808080);
            return;
        }
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.name").append(":"), 16, 22, 0x808080);
        g.drawString(font, Component.translatable("ae2lt.gui.frequency.security").append(": "), 16, 52, 0x808080);
    }

    private static Component getSecurityLabel(FrequencySecurityLevel level) {
        return switch (level) {
            case PUBLIC -> Component.translatable("ae2lt.gui.security.public");
            case ENCRYPTED -> Component.translatable("ae2lt.gui.security.encrypted");
            case PRIVATE -> Component.translatable("ae2lt.gui.security.private");
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    // Helpers

    private boolean isSelf(UUID uuid) {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(uuid);
    }

    private FrequencyAccessLevel selfAccess() {
        var members = ClientFrequencyCache.getMembers(freqMenu().getCurrentFrequencyId());
        var mc = Minecraft.getInstance();
        if (mc.player == null) return FrequencyAccessLevel.BLOCKED;
        UUID me = mc.player.getUUID();
        for (var m : members) {
            if (m.uuid().equals(me)) return m.access();
        }
        // fall back to frequency security: PUBLIC → USER, else BLOCKED
        var freq = ClientFrequencyCache.getFrequency(freqMenu().getCurrentFrequencyId());
        if (freq != null && freq.security() == FrequencySecurityLevel.PUBLIC) {
            return FrequencyAccessLevel.USER;
        }
        return FrequencyAccessLevel.BLOCKED;
    }

    private boolean hasEditAccess() {
        return selfAccess().canEdit();
    }

    private boolean hasOwnerAccess() {
        return selfAccess().canDelete();
    }

    private static String accessShort(FrequencyAccessLevel a) {
        return switch (a) {
            case OWNER -> "O";
            case ADMIN -> "A";
            case USER -> "U";
            case BLOCKED -> "B";
        };
    }

    private static String tabIcon(FrequencyNavigationTab tab) {
        return switch (tab) {
            case TAB_HOME -> "\u2302";       // ⌂
            case TAB_SELECTION -> "\u25A4";  // ▤
            case TAB_CONNECTION -> "\u21C4"; // ⇄
            case TAB_MEMBER -> "\u263B";     // ☻
            case TAB_SETTING -> "\u2699";    // ⚙
            case TAB_CREATE -> "+";
        };
    }
}
