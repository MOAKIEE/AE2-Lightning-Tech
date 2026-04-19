package com.moakiee.ae2lt.overload.armor;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public final class OverloadArmorCarrierLocator {
    private final CarrierType type;
    @Nullable
    private final String curiosIdentifier;
    private final int curiosSlotIndex;

    private OverloadArmorCarrierLocator(CarrierType type, @Nullable String curiosIdentifier, int curiosSlotIndex) {
        this.type = type;
        this.curiosIdentifier = curiosIdentifier;
        this.curiosSlotIndex = curiosSlotIndex;
    }

    public static OverloadArmorCarrierLocator chest() {
        return new OverloadArmorCarrierLocator(CarrierType.CHEST, null, -1);
    }

    public static OverloadArmorCarrierLocator curios(String identifier, int slotIndex) {
        return new OverloadArmorCarrierLocator(CarrierType.CURIOS, identifier, slotIndex);
    }

    @Nullable
    public static CarrierAccess findEquipped(Player player, UUID armorId) {
        var chestAccess = chest().resolve(player, armorId);
        if (chestAccess != null) {
            return chestAccess;
        }

        return CuriosApi.getCuriosInventory(player)
                .map(handler -> findCuriosCarrier(handler, armorId))
                .orElse(null);
    }

    @Nullable
    public static CarrierAccess findFirstEquipped(Player player, Predicate<ItemStack> predicate) {
        var chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chestStack.isEmpty() && predicate.test(chestStack)) {
            return new CarrierAccess(chest(), chestStack, stack -> player.setItemSlot(EquipmentSlot.CHEST, stack));
        }

        return CuriosApi.getCuriosInventory(player)
                .map(handler -> findFirstCuriosCarrier(handler, predicate))
                .orElse(null);
    }

    @Nullable
    public CarrierAccess resolve(Player player, UUID armorId) {
        return switch (type) {
            case CHEST -> resolveChest(player, armorId);
            case CURIOS -> resolveCurios(player, armorId);
        };
    }

    /**
     * Forces a client-side resync of the carrier stack. Vanilla's equipment tracker sends
     * {@code ClientboundSetEquipmentPacket} only to *other* players tracking the entity, never
     * to the owner itself — the owner normally sees equipment changes via their open player-
     * inventory menu. Our custom menus don't expose the armor slot as a menu slot, so stacks
     * mutated server-side (e.g. submodule option edits) would stay stale on the client until
     * the menu is reopened. This helper closes that gap by pushing the updated stack directly
     * to the owning client's inventory (for chest slot) or invoking Curios' own sync path (for
     * Curios slots).
     */
    public void resyncToClient(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        switch (type) {
            case CHEST -> {
                var stack = player.getItemBySlot(EquipmentSlot.CHEST);
                // Player inventory container id is -2 (see ClientboundContainerSetSlotPacket
                // docs); chest armor lives at absolute inventory index 38 (36 main + hotbar +
                // 2 for the CHEST armor offset inside Inventory.armor).
                int chestInvSlot = Inventory.INVENTORY_SIZE + EquipmentSlot.CHEST.getIndex();
                serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                        -2, 0, chestInvSlot, stack.copy()));
            }
            case CURIOS -> {
                if (curiosIdentifier == null || curiosSlotIndex < 0) {
                    return;
                }
                CuriosApi.getCuriosInventory(serverPlayer).ifPresent(handler -> {
                    var stacksHandler = handler.getStacksHandler(curiosIdentifier).orElse(null);
                    if (stacksHandler == null || curiosSlotIndex >= stacksHandler.getSlots()) {
                        return;
                    }
                    // Re-committing the live stack is enough for Curios' dirty tracker to queue
                    // the sync on its next pass without firing equip/unequip (stack identity
                    // preserved via copy-and-set).
                    var stack = stacksHandler.getStacks().getStackInSlot(curiosSlotIndex);
                    handler.setEquippedCurio(curiosIdentifier, curiosSlotIndex, stack.copy());
                });
            }
        }
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        if (type == CarrierType.CURIOS) {
            buf.writeUtf(Objects.requireNonNull(curiosIdentifier));
            buf.writeVarInt(curiosSlotIndex);
        }
    }

    public static OverloadArmorCarrierLocator readFromPacket(FriendlyByteBuf buf) {
        var type = buf.readEnum(CarrierType.class);
        if (type == CarrierType.CURIOS) {
            return curios(buf.readUtf(), buf.readVarInt());
        }
        return chest();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OverloadArmorCarrierLocator other)) {
            return false;
        }
        return type == other.type
                && curiosSlotIndex == other.curiosSlotIndex
                && Objects.equals(curiosIdentifier, other.curiosIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, curiosIdentifier, curiosSlotIndex);
    }

    @Override
    public String toString() {
        if (type == CarrierType.CURIOS) {
            return "curios:" + curiosIdentifier + "[" + curiosSlotIndex + "]";
        }
        return "equipment:chest";
    }

    @Nullable
    private CarrierAccess resolveChest(Player player, UUID armorId) {
        var armor = player.getItemBySlot(EquipmentSlot.CHEST);
        var foundArmorId = OverloadArmorState.getArmorId(armor);
        if (foundArmorId == null || !armorId.equals(foundArmorId)) {
            return null;
        }

        return new CarrierAccess(this, armor, stack -> player.setItemSlot(EquipmentSlot.CHEST, stack));
    }

    @Nullable
    private CarrierAccess resolveCurios(Player player, UUID armorId) {
        if (curiosIdentifier == null || curiosSlotIndex < 0) {
            return null;
        }

        return CuriosApi.getCuriosInventory(player)
                .map(handler -> resolveCurios(handler, armorId))
                .orElse(null);
    }

    @Nullable
    private CarrierAccess resolveCurios(ICuriosItemHandler handler, UUID armorId) {
        var stacksHandler = handler.getStacksHandler(Objects.requireNonNull(curiosIdentifier)).orElse(null);
        if (stacksHandler == null || curiosSlotIndex >= stacksHandler.getSlots()) {
            return null;
        }

        var armor = stacksHandler.getStacks().getStackInSlot(curiosSlotIndex);
        var foundArmorId = OverloadArmorState.getArmorId(armor);
        if (foundArmorId == null || !armorId.equals(foundArmorId)) {
            return null;
        }

        return new CarrierAccess(this, armor, stack -> handler.setEquippedCurio(curiosIdentifier, curiosSlotIndex, stack));
    }

    @Nullable
    private static CarrierAccess findCuriosCarrier(ICuriosItemHandler handler, UUID armorId) {
        for (var entry : handler.getCurios().entrySet()) {
            String identifier = entry.getKey();
            ICurioStacksHandler stacksHandler = entry.getValue();
            var stacks = stacksHandler.getStacks();
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                var armor = stacks.getStackInSlot(slot);
                var foundArmorId = OverloadArmorState.getArmorId(armor);
                if (foundArmorId != null && armorId.equals(foundArmorId)) {
                    final int resolvedSlot = slot;
                    final String resolvedIdentifier = identifier;
                    return new CarrierAccess(
                            curios(resolvedIdentifier, resolvedSlot),
                            armor,
                            stack -> handler.setEquippedCurio(resolvedIdentifier, resolvedSlot, stack));
                }
            }
        }
        return null;
    }

    @Nullable
    private static CarrierAccess findFirstCuriosCarrier(ICuriosItemHandler handler, Predicate<ItemStack> predicate) {
        for (var entry : handler.getCurios().entrySet()) {
            String identifier = entry.getKey();
            ICurioStacksHandler stacksHandler = entry.getValue();
            var stacks = stacksHandler.getStacks();
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                var armor = stacks.getStackInSlot(slot);
                if (!armor.isEmpty() && predicate.test(armor)) {
                    final int resolvedSlot = slot;
                    final String resolvedIdentifier = identifier;
                    return new CarrierAccess(
                            curios(resolvedIdentifier, resolvedSlot),
                            armor,
                            stack -> handler.setEquippedCurio(resolvedIdentifier, resolvedSlot, stack));
                }
            }
        }
        return null;
    }

    private enum CarrierType {
        CHEST,
        CURIOS
    }

    public record CarrierAccess(
            OverloadArmorCarrierLocator locator,
            ItemStack armorStack,
            Consumer<ItemStack> committer
    ) {
        public void commit(ItemStack stack) {
            committer.accept(stack);
        }
    }
}
