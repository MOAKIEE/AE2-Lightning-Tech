package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.SettingsFrom;

import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * BlockEntity for the Overloaded Pattern Provider.
 * <p>
 * Extends vanilla PatternProviderBlockEntity — behaves identically in NORMAL mode.
 * Three extra persisted / synced fields provide the skeleton for future wireless mode.
 * <p>
 * PUSH_DIRECTION (block orientation) is always kept and never repurposed:
 * in NORMAL mode it drives adjacent-machine interaction (vanilla semantics);
 * in WIRELESS mode it is purely visual / grid-connectivity and does NOT affect
 * wireless dispatch or auto-return — those use wireless connector records instead.
 */
public class OverloadedPatternProviderBlockEntity extends PatternProviderBlockEntity {

    // -- Custom fields --

    /** Operating mode: NORMAL (adjacent) or WIRELESS (remote). */
    public enum ProviderMode { NORMAL, WIRELESS }

    /** Wireless dispatch strategy: one machine at a time, or split evenly. */
    public enum WirelessStrategy { SINGLE_TARGET, EVEN_DISTRIBUTION }

    private ProviderMode providerMode = ProviderMode.NORMAL;
    private boolean autoReturn = false;
    private WirelessStrategy wirelessStrategy = WirelessStrategy.SINGLE_TARGET;

    // -- Constructor --

    public OverloadedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get(), pos, blockState);
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new PatternProviderLogic(this.getMainNode(), this, 36);
    }

    // -- Getters / Setters --

    public ProviderMode getProviderMode() {
        return providerMode;
    }

    public void setProviderMode(ProviderMode providerMode) {
        this.providerMode = providerMode;
        saveChanges();
    }

    public boolean isAutoReturn() {
        return autoReturn;
    }

    public void setAutoReturn(boolean autoReturn) {
        this.autoReturn = autoReturn;
        saveChanges();
    }

    public WirelessStrategy getWirelessStrategy() {
        return wirelessStrategy;
    }

    public void setWirelessStrategy(WirelessStrategy wirelessStrategy) {
        this.wirelessStrategy = wirelessStrategy;
        saveChanges();
    }

    // -- NBT persistence --

    private static final String TAG_PROVIDER_MODE = "OverloadMode";
    private static final String TAG_AUTO_RETURN = "AutoReturn";
    private static final String TAG_WIRELESS_STRATEGY = "WirelessStrategy";

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putString(TAG_PROVIDER_MODE, providerMode.name());
        data.putBoolean(TAG_AUTO_RETURN, autoReturn);
        data.putString(TAG_WIRELESS_STRATEGY, wirelessStrategy.name());
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        if (data.contains(TAG_PROVIDER_MODE)) {
            try {
                providerMode = ProviderMode.valueOf(data.getString(TAG_PROVIDER_MODE));
            } catch (IllegalArgumentException ignored) {
                providerMode = ProviderMode.NORMAL;
            }
        }
        autoReturn = data.getBoolean(TAG_AUTO_RETURN);
        if (data.contains(TAG_WIRELESS_STRATEGY)) {
            try {
                wirelessStrategy = WirelessStrategy.valueOf(data.getString(TAG_WIRELESS_STRATEGY));
            } catch (IllegalArgumentException ignored) {
                wirelessStrategy = WirelessStrategy.SINGLE_TARGET;
            }
        }
    }

    // -- Menu binding --

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(OverloadedPatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(OverloadedPatternProviderMenu.TYPE, player, subMenu.getLocator());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
    }
}
