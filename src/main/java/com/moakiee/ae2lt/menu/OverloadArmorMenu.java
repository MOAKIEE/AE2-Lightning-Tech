package com.moakiee.ae2lt.menu;

import java.util.List;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.overload.armor.OverloadArmorMenuLocator;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
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

    private static final long CONFIG_DIRECTION_BIT = 1L << 62;

    private final OverloadArmorHost host;
    private int clientAppliedFeatureMask = Integer.MIN_VALUE;

    public OverloadArmorMenu(int id, Inventory playerInventory, OverloadArmorHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        registerClientAction("toggleFeature", Integer.class, this::toggleFeature);
        registerClientAction("toggleSubmoduleConfig", Long.class, this::toggleSubmoduleConfig);
        updateSnapshot();
    }

    public void clientToggleFeature(int index) {
        sendClientAction("toggleFeature", index);
    }

    public void clientToggleSubmoduleConfig(int submoduleIndex, int configIndex) {
        clientCycleSubmoduleConfig(submoduleIndex, configIndex, true);
    }

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
        if (equippedFlag == 0) {
            return Component.translatable("ae2lt.overload_armor.status.not_equipped");
        }
        return Component.translatable("ae2lt.overload_armor.status.ready");
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

    public int getSubmoduleInstalledAmount(int index) {
        var submodule = getSubmodule(index);
        if (submodule == null) {
            return 0;
        }
        return OverloadArmorState.getInstalledAmount(host.getItemStack(), registryAccess(), submodule.id());
    }

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

    public List<OverloadArmorSubmoduleOptionUi> getSubmoduleConfigUi(int index) {
        var submodule = getSubmodule(index);
        return submodule != null ? submodule.getConfigUI(host.getItemStack()) : List.of();
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
        equippedFlag = snapshot.equipped() ? 1 : 0;
        coreInstalled = snapshot.hasCore() ? 1 : 0;
        bufferInstalled = snapshot.hasBuffer() ? 1 : 0;
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
        resyncCarrierStack();
    }

    private void applySubmoduleConfigChange(OverloadArmorSubmodule submodule, String optionKey, boolean forward) {
        var nextValue = createNextConfigValue(submodule, optionKey, forward);
        if (nextValue == null || !submodule.setConfig(host.getItemStack(), optionKey, nextValue)) {
            return;
        }

        syncSubmoduleState();
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
}
