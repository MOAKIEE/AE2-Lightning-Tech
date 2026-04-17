package com.moakiee.ae2lt.menu;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadArmorWorkbenchBlockEntity;
import com.moakiee.ae2lt.item.LightningStorageComponentItem;
import com.moakiee.ae2lt.item.OverloadArmorItem;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.registry.ModItems;

/**
 * Workbench container menu. The layout is vertical on the left (armor on top, then core, buffer,
 * terminal in a single column) with a scrollable module list on the right and a single "install"
 * input slot beneath the list where the player drops submodule items. The server auto-consumes
 * one item at a time from the input into the armor's module list whenever the idle overload
 * budget still has room, Mekanism-style.
 *
 * <p>Installed modules are NOT backed by a fixed grid of physical slots any more — they live in
 * the armor's own CUSTOM_DATA as a dynamic ItemStack list, one entry per distinct submodule type
 * with {@link ItemStack#getCount()} = installed amount. The screen reads that list directly via
 * {@link #getInstalledModuleList()} for display and uses a
 * {@link net.minecraft.world.inventory.AbstractContainerMenu}-level client action to request
 * per-id uninstalls.
 */
public class OverloadArmorWorkbenchMenu extends AEBaseMenu {
    public static final MenuType<OverloadArmorWorkbenchMenu> TYPE = MenuTypeBuilder
            .create(OverloadArmorWorkbenchMenu::new, OverloadArmorWorkbenchBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.overload_armor_workbench"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overload_armor_workbench"));

    // Left column coordinates (armor on top, structural slots below).
    public static final int LEFT_COL_X = 8;
    public static final int ARMOR_Y = 20;
    public static final int CORE_Y = 46;
    public static final int BUFFER_Y = 66;
    public static final int TERMINAL_Y = 86;

    // Module list area (read-only display, rendered by screen). Input slot sits below the list.
    public static final int LIST_X = 32;
    public static final int LIST_Y = 20;
    public static final int LIST_WIDTH = 156;
    public static final int LIST_HEIGHT = 72;
    public static final int INPUT_X = LEFT_COL_X + 26;
    public static final int INPUT_Y = LIST_Y + LIST_HEIGHT + 12;

    // Player inventory layout.
    public static final int INVENTORY_X = 20;
    public static final int INVENTORY_Y = 124;
    public static final int HOTBAR_Y = 182;

    @GuiSync(0)
    public int armorPresent;
    @GuiSync(1)
    public int moduleTypeCount;
    @GuiSync(2)
    public int baseOverload;
    @GuiSync(3)
    public long bufferCapacity;
    @GuiSync(4)
    public int coreInstalled;
    @GuiSync(5)
    public int bufferInstalled;
    @GuiSync(6)
    public int terminalInstalled;
    @GuiSync(7)
    public int moduleIdleUsed;

    private final OverloadArmorWorkbenchBlockEntity host;
    private final Slot armorSlot;
    private final Slot coreSlot;
    private final Slot bufferSlot;
    private final Slot terminalSlot;
    private final Slot inputSlot;
    private final SimpleContainer inputContainer = new SimpleContainer(1);

    public OverloadArmorWorkbenchMenu(int id, Inventory playerInventory, OverloadArmorWorkbenchBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        var structuralContainer = new StructuralSlotContainer();
        armorSlot = addSlot(
                new WorkbenchArmorSlot(host.getArmorInventory(), 0, LEFT_COL_X, ARMOR_Y),
                Ae2ltSlotSemantics.OVERLOAD_ARMOR_WORKBENCH_ARMOR);
        coreSlot = addSlot(
                new CoreStructuralSlot(structuralContainer, 0, LEFT_COL_X, CORE_Y),
                Ae2ltSlotSemantics.OVERLOAD_ARMOR_WORKBENCH_CORE);
        bufferSlot = addSlot(
                new StructuralSlot(structuralContainer, 1, LEFT_COL_X, BUFFER_Y,
                        stack -> stack.getItem() instanceof LightningStorageComponentItem),
                Ae2ltSlotSemantics.OVERLOAD_ARMOR_WORKBENCH_BUFFER);
        terminalSlot = addSlot(
                new StructuralSlot(structuralContainer, 2, LEFT_COL_X, TERMINAL_Y,
                        stack -> stack.getItem() instanceof ItemWT),
                Ae2ltSlotSemantics.OVERLOAD_ARMOR_WORKBENCH_TERMINAL);

        // Single install-input slot. Stack size is deliberately full (64) so the player can dump a
        // whole stack and watch the workbench eat them one by one until the idle budget fills up.
        inputSlot = addSlot(
                new ModuleInputSlot(inputContainer, 0, INPUT_X, INPUT_Y),
                Ae2ltSlotSemantics.OVERLOAD_ARMOR_WORKBENCH_MODULE);

        addPlayerInventorySlots(playerInventory);

        // Client action channels: the screen's X-button sends the list index the user clicked on;
        // the server looks up the current list, resolves the submodule id, and pops one instance
        // (or the full count, when the player held shift). Two separate actions keep the
        // AE2 single-arg channel contract.
        registerClientAction("uninstallModuleAtIndex", Integer.class, this::handleUninstallAt);
        registerClientAction("uninstallAllOfIndex", Integer.class, this::handleUninstallAllAt);

        updateSnapshot();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            autoConsumeInput();
            updateSnapshot();
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        // Hand any residual input back to the player so modules don't disappear when the menu
        // closes mid-install.
        if (isServerSide()) {
            var residual = inputContainer.removeItemNoUpdate(0);
            if (!residual.isEmpty()) {
                if (!player.getInventory().add(residual)) {
                    player.drop(residual, false);
                }
            }
        }
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isClientSide() || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = getSlot(index);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack original = sourceStack.copy();
        ItemStack remainder;

        if (isPlayerSideSlot(sourceSlot)) {
            var destinations = getWorkbenchDestinationSlots(sourceStack);
            if (destinations.isEmpty()) {
                return ItemStack.EMPTY;
            }
            remainder = moveIntoSlots(sourceStack.copy(), destinations);
        } else {
            remainder = moveIntoSlots(sourceStack.copy(), getPlayerDestinationSlots());
        }

        int moved = original.getCount() - remainder.getCount();
        if (moved <= 0) {
            return ItemStack.EMPTY;
        }

        sourceSlot.remove(moved);
        sourceSlot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (host.isRemoved() || host.getLevel() == null) {
            return false;
        }

        return host.getLevel().getBlockEntity(host.getBlockPos()) == host
                && player.level() == host.getLevel()
                && player.distanceToSqr(
                        host.getBlockPos().getX() + 0.5D,
                        host.getBlockPos().getY() + 0.5D,
                        host.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    public boolean hasArmorInserted() {
        return armorPresent != 0;
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

    public Component getStatusText() {
        if (!hasArmorInserted()) {
            return Component.translatable("ae2lt.overload_armor_workbench.status.no_armor");
        }
        if (!hasCoreInstalled()) {
            return Component.translatable("ae2lt.overload_armor.status.missing_core");
        }
        if (!hasBufferInstalled()) {
            return Component.translatable("ae2lt.overload_armor.status.missing_buffer");
        }
        if (!hasTerminalInstalled()) {
            return Component.translatable("ae2lt.overload_armor.status.missing_terminal");
        }
        return Component.translatable("ae2lt.overload_armor_workbench.status.ready");
    }

    /**
     * Read the armor's installed-module list from the currently-displayed armor stack. Both sides
     * see the same list because the armor slot content (including CUSTOM_DATA) is synced through
     * the AE2 internal inventory — no bespoke sync channel is required for display data.
     */
    public List<ItemStack> getInstalledModuleList() {
        return host.getArmorModuleList(registryAccess());
    }

    /**
     * Screen-side helper: request uninstall for the module rendered at {@code index} of
     * {@link #getInstalledModuleList()}. {@code all=false} pops one instance, {@code all=true}
     * empties the entry in one go (used by shift-click). On the client this just forwards the
     * click to the server; on the server it executes the uninstall.
     */
    public void requestUninstall(int index, boolean all) {
        sendClientAction(all ? "uninstallAllOfIndex" : "uninstallModuleAtIndex", index);
    }

    private void handleUninstallAt(int index) {
        if (isClientSide()) return;
        var list = host.getArmorModuleList(registryAccess());
        if (index < 0 || index >= list.size()) return;
        var stack = list.get(index);
        if (stack.isEmpty()) return;
        String id = resolveSubmoduleId(stack);
        if (id.isBlank()) return;
        var detached = host.uninstallOneModule(registryAccess(), id);
        if (detached.isEmpty()) return;
        giveToPlayer(detached);
    }

    private void handleUninstallAllAt(int index) {
        if (isClientSide()) return;
        var list = host.getArmorModuleList(registryAccess());
        if (index < 0 || index >= list.size()) return;
        var stack = list.get(index);
        if (stack.isEmpty()) return;
        String id = resolveSubmoduleId(stack);
        if (id.isBlank()) return;
        var detached = host.uninstallAllOfModule(registryAccess(), id);
        if (detached.isEmpty()) return;
        // The lump can exceed the item's maxStackSize when idle was large enough to allow stacks
        // bigger than 64 (future-proofing). Split into maxStackSize chunks and hand each over so
        // Inventory.add can merge without overflow surprises.
        int max = Math.max(1, detached.getMaxStackSize());
        while (!detached.isEmpty()) {
            var chunk = detached.split(Math.min(max, detached.getCount()));
            giveToPlayer(chunk);
        }
    }

    private void giveToPlayer(ItemStack stack) {
        if (stack.isEmpty()) return;
        var player = getPlayer();
        if (player == null) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static String resolveSubmoduleId(ItemStack stack) {
        if (stack.isEmpty()
                || !(stack.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
            return "";
        }
        var ref = new String[]{""};
        provider.collectSubmodules(stack, submodule -> {
            if (submodule != null && !submodule.id().isBlank() && ref[0].isEmpty()) {
                ref[0] = submodule.id();
            }
        });
        return ref[0];
    }

    /**
     * Drains up to {@link InstallBatchLimit} items from the input slot per tick, installing them
     * one at a time until the armor runs out of idle budget or the stack is exhausted. A per-tick
     * cap keeps the workbench responsive when the player dumps a huge stack — rather than freezing
     * while installing 64 modules in one frame, we trickle them in and the UI updates naturally.
     */
    private void autoConsumeInput() {
        if (!host.hasInstalledArmor()) return;
        var stack = inputContainer.getItem(0);
        if (stack.isEmpty()) return;
        int budget = InstallBatchLimit;
        while (budget-- > 0 && !stack.isEmpty()) {
            var unit = stack.copyWithCount(1);
            if (!host.installOneModule(registryAccess(), unit)) {
                break;
            }
            stack.shrink(1);
        }
        inputContainer.setItem(0, stack.isEmpty() ? ItemStack.EMPTY : stack);
    }

    private static final int InstallBatchLimit = 8;

    private void updateSnapshot() {
        var armor = host.getInstalledArmor();
        armorPresent = armor.isEmpty() ? 0 : 1;
        if (armor.isEmpty()) {
            moduleTypeCount = 0;
            baseOverload = 0;
            bufferCapacity = 0L;
            coreInstalled = 0;
            bufferInstalled = 0;
            terminalInstalled = 0;
            moduleIdleUsed = 0;
            return;
        }

        var snapshot = OverloadArmorState.snapshot(getPlayer(), armor, registryAccess(), false);
        moduleTypeCount = OverloadArmorState.loadModuleStacks(armor, registryAccess()).size();
        baseOverload = snapshot.baseOverload();
        bufferCapacity = snapshot.bufferCapacity();
        coreInstalled = snapshot.hasCore() ? 1 : 0;
        bufferInstalled = snapshot.hasBuffer() ? 1 : 0;
        terminalInstalled = snapshot.hasTerminal() ? 1 : 0;
        moduleIdleUsed = OverloadArmorState.computeTotalIdleOverload(armor, registryAccess());
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slotIndex = column + row * 9 + 9;
                addSlot(new Slot(
                                playerInventory,
                                slotIndex,
                                INVENTORY_X + column * 18,
                                INVENTORY_Y + row * 18),
                        SlotSemantics.PLAYER_INVENTORY);
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, INVENTORY_X + column * 18, HOTBAR_Y),
                    SlotSemantics.PLAYER_HOTBAR);
        }
    }

    private List<Slot> getWorkbenchDestinationSlots(ItemStack stack) {
        if (stack.getItem() instanceof OverloadArmorItem) {
            return List.of(armorSlot);
        }
        if (stack.is(ModItems.ULTIMATE_OVERLOAD_CORE.get())) {
            return List.of(coreSlot);
        }
        if (stack.getItem() instanceof LightningStorageComponentItem) {
            return List.of(bufferSlot);
        }
        if (stack.getItem() instanceof ItemWT) {
            return List.of(terminalSlot);
        }
        if (stack.getItem() instanceof OverloadArmorSubmoduleItem) {
            return List.of(inputSlot);
        }
        return List.of();
    }

    private List<Slot> getPlayerDestinationSlots() {
        var result = new java.util.ArrayList<Slot>(getSlots(SlotSemantics.PLAYER_INVENTORY));
        result.addAll(getSlots(SlotSemantics.PLAYER_HOTBAR));
        return result;
    }

    private static ItemStack moveIntoSlots(ItemStack stack, List<Slot> destinations) {
        ItemStack remainder = stack;
        for (Slot slot : destinations) {
            if (!slot.hasItem()) {
                continue;
            }
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        for (Slot slot : destinations) {
            if (slot.hasItem()) {
                continue;
            }
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remainder;
    }

    private final class StructuralSlotContainer extends SimpleContainer {
        private StructuralSlotContainer() {
            super(3);
        }

        @Override
        public int getContainerSize() {
            return 3;
        }

        @Override
        public ItemStack getItem(int slot) {
            return host.getArmorStructuralSlot(registryAccess(), toArmorSlot(slot));
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return host.removeArmorStructuralSlot(registryAccess(), toArmorSlot(slot), amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return host.removeArmorStructuralSlot(registryAccess(), toArmorSlot(slot), Integer.MAX_VALUE);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            host.setArmorStructuralSlot(registryAccess(), toArmorSlot(slot), stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return OverloadArmorWorkbenchMenu.this.stillValid(player);
        }

        private int toArmorSlot(int slot) {
            return switch (slot) {
                case 0 -> OverloadArmorState.SLOT_CORE;
                case 1 -> OverloadArmorState.SLOT_BUFFER;
                case 2 -> OverloadArmorState.SLOT_TERMINAL;
                default -> -1;
            };
        }
    }

    private static final class WorkbenchArmorSlot extends AppEngSlot {
        private WorkbenchArmorSlot(InternalInventory inventory, int invSlot, int x, int y) {
            super(inventory, invSlot);
            this.x = x;
            this.y = y;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private class StructuralSlot extends Slot {
        private final Predicate<ItemStack> validator;

        private StructuralSlot(
                StructuralSlotContainer container,
                int slot,
                int x,
                int y,
                Predicate<ItemStack> validator
        ) {
            super(container, slot, x, y);
            this.validator = validator;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return host.hasInstalledArmor() && !stack.isEmpty() && validator.test(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return host.hasInstalledArmor() && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /**
     * Sophisticated-Storage-style "swap-only" core slot. Accepts any candidate that satisfies
     * {@link OverloadArmorState#canInstallCore} (so downgrade below the installed idle sum is
     * rejected), and refuses bare pickup while any module is installed — the player can only take
     * the core out by putting a replacement on their cursor first, which vanilla's PICKUP click
     * handles as an atomic swap.
     */
    private final class CoreStructuralSlot extends StructuralSlot {
        private CoreStructuralSlot(StructuralSlotContainer container, int slot, int x, int y) {
            super(container, slot, x, y,
                    stack -> stack.is(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return super.mayPlace(stack)
                    && OverloadArmorState.canInstallCore(
                            host.getInstalledArmor(), registryAccess(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (!super.mayPickup(player)) {
                return false;
            }
            if (!OverloadArmorState.hasAnyInstalledModule(
                    host.getInstalledArmor(), registryAccess())) {
                return true;
            }
            var cursor = getCarried();
            return !cursor.isEmpty()
                    && cursor.is(ModItems.ULTIMATE_OVERLOAD_CORE.get())
                    && OverloadArmorState.canInstallCore(
                            host.getInstalledArmor(), registryAccess(), cursor);
        }
    }

    /**
     * Install-input slot. Accepts any {@link OverloadArmorSubmoduleItem} whose first submodule
     * would still fit under the armor's idle budget; the server-side {@link #autoConsumeInput}
     * pass drains it one instance at a time per tick. The stack size is full (no limit) so the
     * player can dump a 64-stack and watch it tick down as the workbench installs.
     */
    private final class ModuleInputSlot extends Slot {
        private ModuleInputSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!host.hasInstalledArmor()
                    || stack.isEmpty()
                    || !(stack.getItem() instanceof OverloadArmorSubmoduleItem)) {
                return false;
            }
            // We accept the stack even if the budget is "almost full": the drain loop will stop
            // as soon as the next instance doesn't fit, leaving the residual in the slot for the
            // player to pull back out. This matches Mekanism's feel where you can park a stack in
            // the install slot and let it fill as capacity frees up.
            return OverloadArmorState.canInstallModule(
                    host.getInstalledArmor(), registryAccess(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }
}
