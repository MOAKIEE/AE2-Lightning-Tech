package com.moakiee.ae2lt.menu;

import java.util.List;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.overload.armor.OverloadArmorMenuLocator;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.overload.armor.OverloadArmorTerminalService;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorFeature;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleConfig;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleOptionUi;

public class OverloadArmorMenu extends AEBaseMenu {
    public static final MenuType<OverloadArmorMenu> TYPE = MenuTypeBuilder
            .create(OverloadArmorMenu::new, OverloadArmorHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overload_armor"));

    @GuiSync(10)
    public int baseOverload;
    @GuiSync(11)
    public int currentLoad;
    @GuiSync(12)
    public int remainingLoad;
    @GuiSync(13)
    public long bufferCapacity;
    @GuiSync(14)
    public int terminalReady;
    @GuiSync(15)
    public long storedEnergy;
    @GuiSync(16)
    public long unpaidEnergy;
    @GuiSync(17)
    public int debtTicks;
    @GuiSync(18)
    public int lockedTicks;
    @GuiSync(19)
    public int featureCount;
    @GuiSync(20)
    public int featureMask;
    @GuiSync(21)
    public int equippedFlag;
    @GuiSync(22)
    public int coreInstalled;
    @GuiSync(23)
    public int bufferInstalled;
    @GuiSync(24)
    public int terminalInstalled;

    private static final long CONFIG_DIRECTION_BIT = 1L << 62;

    private final OverloadArmorHost host;
    private int clientAppliedFeatureMask = Integer.MIN_VALUE;

    public OverloadArmorMenu(int id, Inventory playerInventory, OverloadArmorHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        registerClientAction("openTerminal", Boolean.class, this::openTerminal);
        registerClientAction("toggleFeature", Integer.class, this::toggleFeature);
        registerClientAction("toggleSubmoduleConfig", Long.class, this::toggleSubmoduleConfig);
        updateSnapshot();
    }

    public void clientOpenTerminal() {
        sendClientAction("openTerminal", Boolean.TRUE);
    }

    public void clientToggleFeature(int index) {
        sendClientAction("toggleFeature", index);
    }

    public void clientToggleSubmoduleConfig(int submoduleIndex, int configIndex) {
        clientCycleSubmoduleConfig(submoduleIndex, configIndex, true);
    }

    /**
     * Cycle the option at (submodule, config) forward ({@code forward=true}) or backward. Forward
     * is what left-click triggers; right-click sends {@code forward=false}. Backward cycling is
     * mainly useful for enums with 3+ options; boolean toggles round-trip either way.
     */
    public void clientCycleSubmoduleConfig(int submoduleIndex, int configIndex, boolean forward) {
        long packed = packConfigAction(submoduleIndex, configIndex) | (forward ? 0L : CONFIG_DIRECTION_BIT);
        sendClientAction("toggleSubmoduleConfig", packed);
    }

    public Component getStatusText() {
        if (lockedTicks > 0) {
            return Component.translatable("ae2lt.overload_armor.status.locked");
        }
        if (coreInstalled == 0) {
            return Component.translatable("ae2lt.overload_armor.status.missing_core");
        }
        if (bufferInstalled == 0) {
            return Component.translatable("ae2lt.overload_armor.status.missing_buffer");
        }
        if (terminalInstalled == 0) {
            return Component.translatable("ae2lt.overload_armor.status.missing_terminal");
        }
        if (hasTerminalProxyFeature() && !isTerminalProxyEnabled()) {
            return Component.translatable("ae2lt.overload_armor.status.terminal_disabled");
        }
        if (equippedFlag == 0) {
            return Component.translatable("ae2lt.overload_armor.status.not_equipped");
        }
        if (terminalReady != 0) {
            return Component.translatable("ae2lt.overload_armor.status.ready");
        }
        return Component.translatable("ae2lt.overload_armor.status.terminal_unavailable");
    }

    public int getFeatureCount() {
        return featureCount;
    }

    public int getEnabledFeatureCount() {
        int enabled = 0;
        int limit = Math.min(featureCount, Integer.SIZE - 1);
        for (int index = 0; index < limit; index++) {
            if (isSubmoduleEnabled(index)) {
                enabled++;
            }
        }
        return enabled;
    }

    public int getSubmoduleCount() {
        return featureCount;
    }

    public boolean isFeatureEnabled(int index) {
        return index >= 0 && index < Integer.SIZE - 1 && (featureMask & (1 << index)) != 0;
    }

    public boolean isSubmoduleEnabled(int index) {
        return isFeatureEnabled(index);
    }

    public boolean isEquipped() {
        return equippedFlag != 0;
    }

    public boolean hasCoreInstalled() {
        return coreInstalled != 0;
    }

    public boolean hasBufferInstalled() {
        return bufferInstalled != 0;
    }

    public boolean hasTerminalInstalled() {
        return terminalInstalled != 0;
    }

    @Nullable
    public OverloadArmorFeature getFeature(int index) {
        var submodule = getSubmodule(index);
        return submodule instanceof OverloadArmorFeature feature ? feature : null;
    }

    @Nullable
    public OverloadArmorSubmodule getSubmodule(int index) {
        var submodules = getSubmodules();
        return index >= 0 && index < submodules.size() ? submodules.get(index) : null;
    }

    public Component getSubmoduleButtonText(int index) {
        var submodule = getSubmodule(index);
        return submodule != null ? submodule.buttonLabel(isSubmoduleEnabled(index)) : Component.empty();
    }

    public Component getSubmoduleTooltipText(int index) {
        var submodule = getSubmodule(index);
        return submodule != null ? submodule.description() : Component.empty();
    }

    public Component getSubmoduleName(int index) {
        var submodule = getSubmodule(index);
        return submodule != null ? submodule.name() : Component.empty();
    }

    /**
     * Returns how many instances of the given submodule type are installed on the armor. The V
     * menu lists one row per distinct type, so this is used to render an "×N" badge when the
     * player has stacked the same module type.
     */
    public int getSubmoduleInstalledAmount(int index) {
        var submodule = getSubmodule(index);
        if (submodule == null) {
            return 0;
        }
        return OverloadArmorState.getInstalledAmount(host.getItemStack(), registryAccess(), submodule.id());
    }

    /**
     * Returns the per-type install cap of the submodule at {@code index}, or {@code 0} if the
     * submodule is uncapped. The screen appends "/cap" to the amount badge whenever this is
     * positive so the player always sees their remaining slots for that type.
     */
    public int getSubmoduleMaxInstallAmount(int index) {
        var submodule = getSubmodule(index);
        return submodule != null ? OverloadArmorState.getSubmoduleMaxInstallAmount(submodule.id()) : 0;
    }

    public int getSubmoduleIdleOverloaded(int index) {
        var submodule = getSubmodule(index);
        return submodule != null
                ? submodule.getIdleOverloaded(getPlayer(), resolveDist(), host.getItemStack())
                : 0;
    }

    public int getSubmoduleDynamicOverloaded(int index) {
        var submodule = getSubmodule(index);
        return submodule != null
                ? OverloadArmorState.getSubmoduleDynamicLoad(host.getItemStack(), submodule)
                : 0;
    }

    public boolean doesSubmoduleGrantTerminalAccess(int index) {
        var submodule = getSubmodule(index);
        return submodule != null && submodule.grantsTerminalAccess();
    }

    public List<OverloadArmorSubmoduleOptionUi> getSubmoduleConfigUi(int index) {
        var submodule = getSubmodule(index);
        return submodule != null ? submodule.getConfigUI(host.getItemStack()) : List.of();
    }

    @Deprecated(forRemoval = false)
    public List<OverloadArmorSubmoduleOptionUi> getSubmoduleOptionUi(int index) {
        return getSubmoduleConfigUi(index);
    }

    public Component getFeatureButtonText(int index) {
        return getSubmoduleButtonText(index);
    }

    public Component getFeatureTooltipText(int index) {
        return getSubmoduleTooltipText(index);
    }

    public Component getFeatureName(int index) {
        return getSubmoduleName(index);
    }

    public int getFeatureIdleLoad(int index) {
        return getSubmoduleIdleOverloaded(index);
    }

    public boolean doesFeatureGrantTerminalAccess(int index) {
        return doesSubmoduleGrantTerminalAccess(index);
    }

    public Component getSubmoduleStatusText(int index) {
        var submodule = getSubmodule(index);
        boolean active = submodule != null && OverloadArmorState.isSubmoduleActive(
                host.getItemStack(),
                submodule,
                registryAccess(),
                host.isEquippedCarrier());
        return Component.translatable(active
                ? "ae2lt.overload_armor.screen.module_available"
                : "ae2lt.overload_armor.screen.module_offline");
    }

    public boolean hasTerminalProxyFeature() {
        return getSubmodules().stream().anyMatch(OverloadArmorSubmodule::grantsTerminalAccess);
    }

    public boolean isTerminalProxyEnabled() {
        var submodules = getSubmodules();
        for (int index = 0; index < submodules.size(); index++) {
            if (submodules.get(index).grantsTerminalAccess()) {
                return isSubmoduleEnabled(index);
            }
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            updateSnapshot();
        }
        super.broadcastChanges();
    }

    private void updateSnapshot() {
        var submodules = getSubmodules();
        featureCount = submodules.size();
        featureMask = OverloadArmorState.buildSubmoduleMask(host.getItemStack(), submodules);

        var snapshot = OverloadArmorState.snapshot(
                getPlayer(),
                host.getItemStack(),
                registryAccess(),
                host.isEquippedCarrier());
        baseOverload = snapshot.baseOverload();
        currentLoad = snapshot.currentLoad();
        remainingLoad = snapshot.remainingLoad();
        bufferCapacity = snapshot.bufferCapacity();
        storedEnergy = snapshot.storedEnergy();
        unpaidEnergy = snapshot.unpaidEnergy();
        debtTicks = snapshot.debtTicks();
        lockedTicks = snapshot.lockedTicks();
        terminalReady = snapshot.canOpenTerminal() ? 1 : 0;
        equippedFlag = snapshot.equipped() ? 1 : 0;
        coreInstalled = snapshot.hasCore() ? 1 : 0;
        bufferInstalled = snapshot.hasBuffer() ? 1 : 0;
        terminalInstalled = snapshot.hasTerminal() ? 1 : 0;
    }

    public void syncClientSubmoduleStateFromServer() {
        if (!isClientSide() || clientAppliedFeatureMask == featureMask) {
            return;
        }

        boolean changed = false;
        var submodules = getSubmodules();
        int limit = Math.min(submodules.size(), Integer.SIZE - 1);
        for (int index = 0; index < limit; index++) {
            var submodule = submodules.get(index);
            boolean syncedEnabled = (featureMask & (1 << index)) != 0;
            boolean localEnabled = OverloadArmorState.isSubmoduleEnabled(host.getItemStack(), submodule);
            if (localEnabled != syncedEnabled) {
                OverloadArmorState.setSubmoduleEnabled(host.getItemStack(), submodule, syncedEnabled);
                changed = true;
            }
        }

        clientAppliedFeatureMask = featureMask;
        if (!changed) {
            return;
        }

        OverloadArmorState.syncSubmoduleActiveState(
                getPlayer(),
                host.getItemStack(),
                registryAccess(),
                host.isEquippedCarrier(),
                Dist.CLIENT);
        if (host.isEquippedCarrier()) {
            OverloadArmorState.tickActiveSubmodules(
                    getPlayer(),
                    host.getItemStack(),
                    registryAccess(),
                    Dist.CLIENT);
        }
    }

    private void openTerminal(Boolean ignored) {
        if (!isServerSide() || !(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        boolean opened = OverloadArmorTerminalService.openTerminal(serverPlayer, host.getItemStack());
        if (!opened) {
            serverPlayer.displayClientMessage(
                    Component.translatable("ae2lt.overload_armor.terminal_unavailable"),
                    true);
        }
    }

    private void toggleFeature(Integer featureIndex) {
        if (!isServerSide() || featureIndex == null) {
            return;
        }

        var submodule = getSubmodule(featureIndex);
        if (submodule == null) {
            return;
        }

        applySubmoduleEnabledChange(submodule);
    }

    private void toggleSubmoduleConfig(Long packedAction) {
        if (!isServerSide() || packedAction == null) {
            return;
        }

        boolean forward = (packedAction & CONFIG_DIRECTION_BIT) == 0L;
        long stripped = packedAction & ~CONFIG_DIRECTION_BIT;
        int submoduleIndex = (int) (stripped >> Integer.SIZE);
        int configIndex = (int) stripped;
        var submodule = getSubmodule(submoduleIndex);
        if (submodule == null) {
            return;
        }

        var configUi = submodule.getConfigUI(host.getItemStack());
        if (configIndex < 0 || configIndex >= configUi.size()) {
            return;
        }

        var option = configUi.get(configIndex);
        if (!option.editable()) {
            return;
        }

        applySubmoduleConfigChange(submodule, option.key(), forward);
    }

    private List<OverloadArmorSubmodule> getSubmodules() {
        return OverloadArmorState.collectSubmodules(host.getItemStack(), registryAccess());
    }

    private void applySubmoduleEnabledChange(OverloadArmorSubmodule submodule) {
        boolean enabled = OverloadArmorState.isSubmoduleEnabled(host.getItemStack(), submodule);
        OverloadArmorState.setSubmoduleEnabled(host.getItemStack(), submodule, !enabled);
        syncSubmoduleState();
        // featureMask already round-trips via GuiSync, but submodule toggle edits the stack's
        // CUSTOM_DATA too; resync so the client's view of the stack stays in lock-step with
        // the server (matters for tooltips / UI reading NBT directly).
        resyncCarrierStack();
    }

    private void applySubmoduleConfigChange(OverloadArmorSubmodule submodule, String optionKey, boolean forward) {
        var nextValue = createNextConfigValue(submodule, optionKey, forward);
        if (nextValue == null || !submodule.setConfig(host.getItemStack(), optionKey, nextValue)) {
            return;
        }

        syncSubmoduleState();
        // Config values are only stored in the stack's NBT (not synced via GuiSync). Without
        // this push, the owner-client's config panel keeps rendering the pre-edit values until
        // the menu is reopened, so clicks look like no-ops.
        resyncCarrierStack();
    }

    private void resyncCarrierStack() {
        if (host.getLocator() instanceof OverloadArmorMenuLocator locator) {
            locator.carrierLocator().resyncToClient(getPlayer());
        }
    }

    @Nullable
    private Tag createNextConfigValue(OverloadArmorSubmodule submodule, String optionKey, boolean forward) {
        for (OverloadArmorSubmoduleConfig config : submodule.getConfigs(host.getItemStack())) {
            if (!config.key().equals(optionKey) || !config.editable()) {
                continue;
            }
            return forward ? config.nextValue() : config.previousValue();
        }
        return null;
    }

    private void syncSubmoduleState() {
        OverloadArmorState.syncSubmoduleActiveState(
                getPlayer(),
                host.getItemStack(),
                registryAccess(),
                host.isEquippedCarrier(),
                resolveDist());
        if (host.isEquippedCarrier()) {
            OverloadArmorState.tickActiveSubmodules(
                    getPlayer(),
                    host.getItemStack(),
                    registryAccess(),
                    resolveDist());
        }
        updateSnapshot();
    }

    private static long packConfigAction(int submoduleIndex, int configIndex) {
        return ((long) submoduleIndex << Integer.SIZE) | (configIndex & 0xFFFFFFFFL);
    }

    private Dist resolveDist() {
        return getPlayer() != null && getPlayer().level().isClientSide() ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }

    @Deprecated(forRemoval = false)
    public boolean hasTerminalProxyFeatureLegacy() {
        return hasTerminalProxyFeature();
    }

    @Deprecated(forRemoval = false)
    public boolean isTerminalProxyEnabledLegacy() {
        return isTerminalProxyEnabled();
    }
}
