package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.item.WirelessIdCardItem;

public class WirelessControllerMenu extends AEBaseMenu {

    public static final MenuType<WirelessControllerMenu> TYPE = MenuTypeBuilder
            .create(WirelessControllerMenu::new, WirelessOverloadedControllerBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.wireless_overloaded_controller"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "wireless_controller"));

    @GuiSync(0)
    public String uuidShort = "";

    @GuiSync(1)
    public boolean advanced;

    private final WirelessOverloadedControllerBlockEntity host;
    private final Slot cardSlot;

    public WirelessControllerMenu(int id, Inventory playerInventory, WirelessOverloadedControllerBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;

        this.cardSlot = addSlot(
                new AppEngSlot(host.getCardInventory(), 0),
                Ae2ltSlotSemantics.WIRELESS_ID_CARD);

        createPlayerInventorySlots(playerInventory);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            UUID id = host.getCardUUID();
            uuidShort = id != null ? id.toString().substring(0, 8) : "";
            advanced = host.isAdvanced();
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        if (isClientSide() || idx < 0 || idx >= slots.size()) {
            return ItemStack.EMPTY;
        }

        var sourceSlot = getSlot(idx);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        var sourceStack = sourceSlot.getItem();
        var original = sourceStack.copy();
        ItemStack remainder;

        if (isPlayerSideSlot(sourceSlot)) {
            if (sourceStack.getItem() instanceof WirelessIdCardItem) {
                remainder = moveIntoSlots(sourceStack.copy(), List.of(cardSlot));
            } else {
                return ItemStack.EMPTY;
            }
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

    public WirelessOverloadedControllerBlockEntity getHost() {
        return host;
    }

    public String getUuidShort() {
        return uuidShort;
    }

    public boolean isAdvanced() {
        return advanced;
    }

    private List<Slot> getPlayerDestinationSlots() {
        var result = new ArrayList<Slot>(getSlots(SlotSemantics.PLAYER_INVENTORY));
        result.addAll(getSlots(SlotSemantics.PLAYER_HOTBAR));
        return result;
    }

    private static ItemStack moveIntoSlots(ItemStack stack, List<Slot> destinations) {
        ItemStack remainder = stack;
        for (var slot : destinations) {
            if (!slot.hasItem()) continue;
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        for (var slot : destinations) {
            if (slot.hasItem()) continue;
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }
}
