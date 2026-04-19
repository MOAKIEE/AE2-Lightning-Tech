package com.moakiee.ae2lt.blockentity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.item.OverloadArmorItem;
import com.moakiee.ae2lt.menu.OverloadArmorWorkbenchMenu;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class OverloadArmorWorkbenchBlockEntity extends AENetworkedBlockEntity implements InternalInventoryHost {
    private static final String TAG_ARMOR_INV = "ArmorInv";

    private final AppEngInternalInventory armorInventory = new AppEngInternalInventory(this, 1, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof OverloadArmorItem;
        }
    };

    public OverloadArmorWorkbenchBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OVERLOAD_ARMOR_WORKBENCH.get(), pos, blockState);
        getMainNode().setIdlePowerUsage(0.0D);
    }

    public AppEngInternalInventory getArmorInventory() {
        return armorInventory;
    }

    public ItemStack getInstalledArmor() {
        return armorInventory.getStackInSlot(0);
    }

    public boolean hasInstalledArmor() {
        return !getInstalledArmor().isEmpty();
    }

    // ── Core / buffer / terminal structural slots ───────────────────────────────────────────

    public ItemStack getArmorStructuralSlot(HolderLookup.Provider registries, int armorSlot) {
        return hasInstalledArmor()
                ? OverloadArmorState.getSlot(getInstalledArmor(), registries, armorSlot)
                : ItemStack.EMPTY;
    }

    public void setArmorStructuralSlot(HolderLookup.Provider registries, int armorSlot, ItemStack stack) {
        if (!hasInstalledArmor()) {
            return;
        }

        var armor = getInstalledArmor();
        // Server-side anti-downgrade guard for the core slot: refuse a core swap that would leave
        // the installed idle load above the candidate core's base overload. Without this the menu
        // slot's mayPlace could be bypassed via shift-click / hopper automation paths.
        if (armorSlot == OverloadArmorState.SLOT_CORE
                && !OverloadArmorState.canInstallCore(armor, registries, stack)) {
            return;
        }
        OverloadArmorState.ensureArmorId(armor);
        OverloadArmorState.setSlot(armor, registries, armorSlot, stack.copy());
        saveChanges();
    }

    public ItemStack removeArmorStructuralSlot(HolderLookup.Provider registries, int armorSlot, int amount) {
        if (!hasInstalledArmor() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        var existing = getArmorStructuralSlot(registries, armorSlot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (amount >= existing.getCount()) {
            setArmorStructuralSlot(registries, armorSlot, ItemStack.EMPTY);
            return existing;
        }

        var remaining = existing.copy();
        var removed = remaining.split(amount);
        setArmorStructuralSlot(registries, armorSlot, remaining);
        return removed;
    }

    // ── Module list (installable submodule items, "id → amount" dictionary) ────────────────

    /**
     * Returns the compact list of installed module-type stacks (each stack's count is the
     * installed amount of that type). The order matches the persisted NBT list order, which the
     * workbench screen uses to render the module list deterministically.
     */
    public List<ItemStack> getArmorModuleList(HolderLookup.Provider registries) {
        return hasInstalledArmor()
                ? OverloadArmorState.loadModuleStacks(getInstalledArmor(), registries)
                : List.of();
    }

    /**
     * Installs a single instance of {@code candidate} into the armor. Returns {@code true} iff the
     * install succeeded (i.e. the armor has a core, the candidate is a submodule item, and the
     * idle budget still has room for one more unit). Fires the install-side lifecycle so modules
     * can seed their persistent data.
     */
    public boolean installOneModule(HolderLookup.Provider registries, ItemStack candidate) {
        if (!hasInstalledArmor() || candidate == null || candidate.isEmpty()) {
            return false;
        }
        var armor = getInstalledArmor();
        if (!OverloadArmorState.installOneModule(armor, registries, candidate)) {
            return false;
        }
        OverloadArmorState.ensureArmorId(armor);
        OverloadArmorState.reconcileInstalledSubmodules(null, armor, registries,
                isClientSide() ? Dist.CLIENT : Dist.DEDICATED_SERVER);
        saveChanges();
        return true;
    }

    /**
     * Extracts a single instance of the given submodule type from the armor, returning the
     * resulting {@code count=1} stack ready to be handed to the player. Returns
     * {@link ItemStack#EMPTY} if the type isn't installed.
     */
    public ItemStack uninstallOneModule(HolderLookup.Provider registries, String submoduleId) {
        if (!hasInstalledArmor()) {
            return ItemStack.EMPTY;
        }
        var armor = getInstalledArmor();
        var detached = OverloadArmorState.uninstallOneModule(armor, registries, submoduleId);
        if (detached.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Reconcile after mutation so onUninstalled fires exactly when the last instance of a type
        // is removed (i.e. when its entry disappears from the list entirely).
        OverloadArmorState.reconcileInstalledSubmodules(null, armor, registries,
                isClientSide() ? Dist.CLIENT : Dist.DEDICATED_SERVER);
        saveChanges();
        return detached;
    }

    /**
     * Extracts every installed instance of the given submodule type, returning the full count in
     * a single stack (may exceed item maxStackSize — callers must split before handing to a
     * player). Reconciles lifecycle afterwards so the module's {@code onUninstalled} fires exactly
     * once.
     */
    public ItemStack uninstallAllOfModule(HolderLookup.Provider registries, String submoduleId) {
        if (!hasInstalledArmor()) {
            return ItemStack.EMPTY;
        }
        var armor = getInstalledArmor();
        var detached = OverloadArmorState.uninstallAllOfType(armor, registries, submoduleId);
        if (detached.isEmpty()) {
            return ItemStack.EMPTY;
        }
        OverloadArmorState.reconcileInstalledSubmodules(null, armor, registries,
                isClientSide() ? Dist.CLIENT : Dist.DEDICATED_SERVER);
        saveChanges();
        return detached;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(OverloadArmorWorkbenchMenu.TYPE, player, locator);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == armorInventory) {
            var armor = armorInventory.getStackInSlot(slot);
            if (armor.getItem() instanceof OverloadArmorItem) {
                OverloadArmorState.ensureArmorId(armor);
            }
            saveChanges();
        }
    }

    @Override
    public boolean isClientSide() {
        return level != null && level.isClientSide();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        armorInventory.writeToNBT(data, TAG_ARMOR_INV, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        armorInventory.readFromNBT(data, TAG_ARMOR_INV, registries);
        var armor = getInstalledArmor();
        if (armor.getItem() instanceof OverloadArmorItem) {
            OverloadArmorState.ensureArmorId(armor);
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        var armor = getInstalledArmor();
        if (!armor.isEmpty()) {
            drops.add(armor.copy());
        }
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.OVERLOAD_ARMOR_WORKBENCH.get().asItem();
    }
}
