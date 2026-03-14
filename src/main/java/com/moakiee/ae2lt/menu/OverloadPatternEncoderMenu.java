package com.moakiee.ae2lt.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.overload.pattern.Ae2PlainPatternResolver;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternEditState;
import com.moakiee.ae2lt.overload.pattern.ParsedPatternDefinition;
import com.moakiee.ae2lt.overload.pattern.PatternConversionService;
import com.moakiee.ae2lt.registry.ModItems;

/**
 * Item-menu for editing per-slot overload match modes.
 * <p>
 * This menu is only a configuration front-end. It parses a source pattern,
 * exposes editable slot modes, and produces a new {@link OverloadPatternItem}.
 */
public class OverloadPatternEncoderMenu extends AEBaseMenu {
    public static final MenuType<OverloadPatternEncoderMenu> TYPE = MenuTypeBuilder
            .create(OverloadPatternEncoderMenu::new, OverloadPatternEncoderHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overload_pattern_encoder"));

    public static final int MAX_INPUT_SLOTS = 18;
    public static final int MAX_OUTPUT_SLOTS = 18;

    public static final int SOURCE_X = 17;
    public static final int SOURCE_Y = 21;
    public static final int RESULT_X = 16;
    public static final int RESULT_Y = 68;
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 107;
    public static final int HOTBAR_X = 8;
    public static final int HOTBAR_Y = 165;

    private static final int OFFSCREEN_SLOT_X = -1000;
    private static final int OFFSCREEN_SLOT_Y = -1000;
    private static final int SLOT_SPACING = 18;

    @GuiSync(20)
    public OverloadPatternEditState syncedState = OverloadPatternEditState.empty();

    private final PatternConversionService conversionService = new PatternConversionService();
    private final AppEngInternalInventory sourceInventory;
    private final AppEngInternalInventory resultInventory;
    private final AppEngInternalInventory inputPreviewInventory;
    private final AppEngInternalInventory outputPreviewInventory;

    @Nullable
    private ParsedPatternDefinition parsedSource;
    private boolean sourceDirty = true;

    public OverloadPatternEncoderMenu(int id, Inventory playerInventory, OverloadPatternEncoderHost host) {
        super(TYPE, id, playerInventory, host);

        this.sourceInventory = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory inv) {
                sourceDirty = true;
                clearEncodedResult();
            }

            @Override
            public boolean isClientSide() {
                return OverloadPatternEncoderMenu.this.isClientSide();
            }
        }, 1, 1);
        this.resultInventory = new AppEngInternalInventory(null, 1, 1);
        this.inputPreviewInventory = new AppEngInternalInventory(null, MAX_INPUT_SLOTS, 1);
        this.outputPreviewInventory = new AppEngInternalInventory(null, MAX_OUTPUT_SLOTS, 1);

        addSlot(new SourcePatternSlot(sourceInventory, 0, SOURCE_X, SOURCE_Y), SlotSemantics.ENCODED_PATTERN);
        addSlot(new ResultPatternSlot(resultInventory, 0, RESULT_X, RESULT_Y), SlotSemantics.CRAFTING_RESULT);

        for (int slot = 0; slot < MAX_INPUT_SLOTS; slot++) {
            addSlot(new PreviewSlot(inputPreviewInventory, slot, OFFSCREEN_SLOT_X, OFFSCREEN_SLOT_Y),
                    SlotSemantics.PROCESSING_INPUTS);
        }
        for (int slot = 0; slot < MAX_OUTPUT_SLOTS; slot++) {
            addSlot(new PreviewSlot(outputPreviewInventory, slot, OFFSCREEN_SLOT_X, OFFSCREEN_SLOT_Y),
                    SlotSemantics.PROCESSING_OUTPUTS);
        }

        addPlayerInventorySlots(playerInventory);

        registerClientAction("toggleInputMode", Integer.class, this::toggleInputMode);
        registerClientAction("toggleOutputMode", Integer.class, this::toggleOutputMode);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && sourceDirty) {
            reloadFromSource();
        }
        if (isServerSide() && parsedSource != null && syncedState.canEncode() && resultInventory.getStackInSlot(0).isEmpty()) {
            encodeResult();
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        returnInventory(player, sourceInventory);
        returnInventory(player, resultInventory);
    }

    public ItemStack getSourceStack() {
        return sourceInventory.getStackInSlot(0);
    }

    public ItemStack getResultStack() {
        return resultInventory.getStackInSlot(0);
    }

    public void clientToggleInputMode(int slotIndex) {
        sendClientAction("toggleInputMode", slotIndex);
    }

    public void clientToggleOutputMode(int slotIndex) {
        sendClientAction("toggleOutputMode", slotIndex);
    }

    public ItemStack getInputPreviewStack(int slotIndex) {
        return slotIndex >= 0 && slotIndex < MAX_INPUT_SLOTS
                ? slots.get(2 + slotIndex).getItem()
                : ItemStack.EMPTY;
    }

    public ItemStack getOutputPreviewStack(int slotIndex) {
        return slotIndex >= 0 && slotIndex < MAX_OUTPUT_SLOTS
                ? slots.get(2 + MAX_INPUT_SLOTS + slotIndex).getItem()
                : ItemStack.EMPTY;
    }

    private void toggleInputMode(int slotIndex) {
        if (!isServerSide() || parsedSource == null) {
            return;
        }
        syncedState = syncedState.toggleInputMode(slotIndex);
        clearEncodedResult();
    }

    private void toggleOutputMode(int slotIndex) {
        if (!isServerSide() || parsedSource == null) {
            return;
        }
        syncedState = syncedState.toggleOutputMode(slotIndex);
        clearEncodedResult();
    }

    private void encodeResult() {
        if (!isServerSide() || parsedSource == null || !syncedState.canEncode()) {
            return;
        }

        var overloadItem = (OverloadPatternItem) ModItems.OVERLOAD_PATTERN.get();
        var stack = conversionService.createOverloadPatternStack(overloadItem, parsedSource, syncedState);
        resultInventory.setItemDirect(0, stack);
    }

    private void reloadFromSource() {
        sourceDirty = false;
        parsedSource = null;
        clearPreviewInventories();
        clearEncodedResult();

        var sourceStack = sourceInventory.getStackInSlot(0);
        if (sourceStack.isEmpty()) {
            syncedState = OverloadPatternEditState.empty();
            return;
        }

        var resolver = new Ae2PlainPatternResolver(getPlayer().level());
        var editable = conversionService.resolveEditableSource(sourceStack, resolver).orElse(null);
        if (editable == null) {
            syncedState = OverloadPatternEditState.empty();
            return;
        }

        parsedSource = editable.parsedPattern();
        syncedState = conversionService.createEditState(
                editable.parsedPattern(),
                editable.encodedPattern(),
                sourceStack.getItem() instanceof OverloadPatternItem);
        populatePreviewInventories(editable.parsedPattern());
    }

    private void populatePreviewInventories(ParsedPatternDefinition parsedPattern) {
        clearPreviewInventories();

        var visibleInputs = Math.min(parsedPattern.inputs().size(), MAX_INPUT_SLOTS);
        for (int i = 0; i < visibleInputs; i++) {
            inputPreviewInventory.setItemDirect(i, normalizedPreview(parsedPattern.inputs().get(i).stack()));
        }

        var visibleOutputs = Math.min(parsedPattern.outputs().size(), MAX_OUTPUT_SLOTS);
        for (int i = 0; i < visibleOutputs; i++) {
            outputPreviewInventory.setItemDirect(i, normalizedPreview(parsedPattern.outputs().get(i).stack()));
        }
    }

    private void clearPreviewInventories() {
        for (int i = 0; i < MAX_INPUT_SLOTS; i++) {
            inputPreviewInventory.setItemDirect(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < MAX_OUTPUT_SLOTS; i++) {
            outputPreviewInventory.setItemDirect(i, ItemStack.EMPTY);
        }
    }

    private void clearEncodedResult() {
        resultInventory.setItemDirect(0, ItemStack.EMPTY);
    }

    private static ItemStack normalizedPreview(ItemStack stack) {
        var copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slotIndex = column + row * 9 + 9;
                addSlot(
                        new Slot(playerInventory, slotIndex, PLAYER_INV_X + column * SLOT_SPACING, PLAYER_INV_Y + row * SLOT_SPACING),
                        SlotSemantics.PLAYER_INVENTORY);
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(
                    new Slot(playerInventory, column, HOTBAR_X + column * SLOT_SPACING, HOTBAR_Y),
                    SlotSemantics.PLAYER_HOTBAR);
        }
    }

    private static void returnInventory(Player player, AppEngInternalInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
            if (stack.isEmpty()) {
                continue;
            }

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static final class SourcePatternSlot extends Slot {
        private SourcePatternSlot(AppEngInternalInventory inventory, int invSlot, int x, int y) {
            super(new InventoryContainerAdapter(inventory), invSlot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static final class PreviewSlot extends Slot {
        private PreviewSlot(AppEngInternalInventory inventory, int invSlot, int x, int y) {
            super(new InventoryContainerAdapter(inventory), invSlot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }
    }

    private static final class ResultPatternSlot extends Slot {
        private ResultPatternSlot(AppEngInternalInventory inventory, int invSlot, int x, int y) {
            super(new InventoryContainerAdapter(inventory), invSlot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    private static final class InventoryContainerAdapter extends SimpleContainer {
        private final AppEngInternalInventory inventory;

        private InventoryContainerAdapter(AppEngInternalInventory inventory) {
            super(inventory.size());
            this.inventory = inventory;
        }

        @Override
        public int getContainerSize() {
            return inventory.size();
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return inventory.extractItem(slot, amount, false);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return inventory.extractItem(slot, Integer.MAX_VALUE, false);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            inventory.setItemDirect(slot, stack);
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.setItemDirect(slot, ItemStack.EMPTY);
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
