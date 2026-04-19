package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.menu.OverloadArmorHost;
import com.moakiee.ae2lt.menu.OverloadArmorMenu;
import com.moakiee.ae2lt.overload.armor.OverloadArmorCarrierLocator;
import com.moakiee.ae2lt.overload.armor.OverloadArmorMenuLocator;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class OverloadArmorItem extends ArmorItem implements IMenuItem, ICurioItem {
    public OverloadArmorItem(Properties properties) {
        super(ArmorMaterials.NETHERITE, Type.CHESTPLATE, properties.stacksTo(1).fireResistant());
    }

    @Override
    public @Nullable OverloadArmorHost getMenuHost(
            Player player,
            ItemMenuHostLocator locator,
            @Nullable BlockHitResult hitResult
    ) {
        return new OverloadArmorHost(this, player, locator);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                OverloadArmorState.ensureArmorId(stack);
                MenuOpener.open(OverloadArmorMenu.TYPE, player, MenuLocators.forHand(player, hand));
            }
            return new InteractionResultHolder<>(
                    InteractionResult.sidedSuccess(level.isClientSide()),
                    stack);
        }

        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!(entity instanceof Player player)) {
            return;
        }

        var armorId = OverloadArmorState.ensureArmorId(stack);
        boolean equippedInChest = player.getItemBySlot(EquipmentSlot.CHEST) == stack;
        if (level.isClientSide()) {
            // Client-side: remember the last stack we saw for this armor so lifecycle packets
            // can still reach a usable ItemStack if the armor has just left the slot.
            cacheClientStack(armorId, stack);
            return;
        }
        // Curios-equipped copies of this armor run through curioTick; the carrier helper lets us
        // detect that case so the inventory-slot call doesn't fight the curios call for state.
        if (!equippedInChest && OverloadArmorCarrierLocator.findEquipped(player, armorId) != null) {
            return;
        }

        var dist = resolveDist(level);
        OverloadArmorState.syncSubmoduleActiveState(
                player,
                stack,
                level.registryAccess(),
                equippedInChest,
                dist);
        if (!equippedInChest) {
            return;
        }
        OverloadArmorState.tickActiveSubmodules(player, stack, level.registryAccess(), dist);
        OverloadArmorState.tickEquipped(player, stack, level.registryAccess());
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) {
            return;
        }

        var armorId = OverloadArmorState.ensureArmorId(stack);
        var level = player.level();
        if (level.isClientSide()) {
            cacheClientStack(armorId, stack);
            return;
        }
        var dist = resolveDist(level);
        OverloadArmorState.syncSubmoduleActiveState(
                player,
                stack,
                level.registryAccess(),
                true,
                dist);
        OverloadArmorState.tickActiveSubmodules(player, stack, level.registryAccess(), dist);
        OverloadArmorState.tickEquipped(player, stack, level.registryAccess());
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) {
            return;
        }
        var level = player.level();
        if (level.isClientSide()) {
            // Client lifecycle is packet-driven from the server to avoid Curios/vanilla slot
            // desync and tick-ordering races.
            return;
        }
        // Curios calls onEquip whenever the stack differs by ItemStack.matches, which includes
        // our per-tick writes. Only react to real armor swaps; cache-driven per-tick sync in
        // curioTick takes care of any transitions that do not correspond to a real swap.
        if (isSameOverloadArmor(prevStack, stack)) {
            return;
        }
        OverloadArmorState.ensureArmorId(stack);
        var dist = resolveDist(level);
        OverloadArmorState.reconcileInstalledSubmodules(player, stack, level.registryAccess(), dist);
        OverloadArmorState.syncSubmoduleActiveState(
                player,
                stack,
                level.registryAccess(),
                true,
                dist);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) {
            return;
        }
        var level = player.level();
        if (level.isClientSide()) {
            return;
        }
        if (isSameOverloadArmor(stack, newStack)) {
            return;
        }
        OverloadArmorState.flushRuntimeToNbt(stack);
        OverloadArmorState.syncSubmoduleActiveState(
                player,
                stack,
                level.registryAccess(),
                false,
                resolveDist(level));
    }

    /**
     * Flush runtime state to NBT on world save so energy/overload progress survives crashes.
     * Iterates every online player's worn / Curios-equipped armor and persists the cached state.
     */
    public static void onLevelSave(net.neoforged.neoforge.event.level.LevelEvent.Save event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (var player : serverLevel.players()) {
                flushPlayerArmor(player);
            }
        }
    }

    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        flushPlayerArmor(event.getEntity());
    }

    /**
     * When a player joins, replay the server's current lifecycle cache to the fresh client so its
     * CLIENT_ACTIVE_CACHE is seeded and each currently-active submodule receives exactly one
     * onActivated call on the client side.
     */
    public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        broadcastInitialLifecycle(serverPlayer);
    }

    private static void broadcastInitialLifecycle(net.minecraft.server.level.ServerPlayer player) {
        var registries = player.level().registryAccess();
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof OverloadArmorItem) {
            broadcastArmorLifecycle(player, chest, registries);
        }
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var entry : handler.getCurios().entrySet()) {
                var stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack slotStack = stacks.getStackInSlot(i);
                    if (slotStack.getItem() instanceof OverloadArmorItem) {
                        broadcastArmorLifecycle(player, slotStack, registries);
                    }
                }
            }
        });
    }

    private static void broadcastArmorLifecycle(
            net.minecraft.server.level.ServerPlayer player,
            ItemStack stack,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        var armorId = OverloadArmorState.ensureArmorId(stack);
        for (var submodule : OverloadArmorState.collectSubmodules(stack, registries)) {
            boolean active = OverloadArmorState.isSubmoduleActive(stack, submodule, registries, true);
            com.moakiee.ae2lt.network.SubmoduleLifecyclePacket.broadcast(
                    player, armorId, submodule.id(), active);
        }
    }

    private static void flushPlayerArmor(Player player) {
        var carrier = OverloadArmorCarrierLocator.findFirstEquipped(
                player,
                s -> s.getItem() instanceof OverloadArmorItem);
        if (carrier != null) {
            var stack = carrier.armorStack();
            if (stack != null && stack.getItem() instanceof OverloadArmorItem) {
                OverloadArmorState.flushRuntimeToNbt(stack);
            }
        }
    }

    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.CHEST || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        var from = event.getFrom();
        var to = event.getTo();
        if (isSameOverloadArmor(from, to)) {
            return;
        }
        if (from.getItem() instanceof OverloadArmorItem) {
            OverloadArmorState.flushRuntimeToNbt(from);
            syncEquipmentState(player, from, false);
        }
        if (to.getItem() instanceof OverloadArmorItem) {
            syncEquipmentState(player, to, true);
        }
    }

    // Client-side cache of the most recently ticked stack per armorId, used by the lifecycle
    // packet handler when the armor has already left the slot (rapid drop, dupe Curios slot).
    private static final Map<UUID, ItemStack> LAST_KNOWN_CLIENT_STACK = new ConcurrentHashMap<>();

    private static void cacheClientStack(UUID armorId, ItemStack stack) {
        if (armorId == null || stack == null || stack.isEmpty()) {
            return;
        }
        LAST_KNOWN_CLIENT_STACK.put(armorId, stack);
    }

    /**
     * Returns (and removes) the last stack we observed on the client for this armorId. Intended
     * as a fallback for deactivate packets that arrive after the stack has already left the slot.
     */
    @Nullable
    public static ItemStack consumeLastKnownClientStack(UUID armorId) {
        if (armorId == null) {
            return null;
        }
        return LAST_KNOWN_CLIENT_STACK.get(armorId);
    }

    private static boolean isSameOverloadArmor(ItemStack a, ItemStack b) {
        if (!(a.getItem() instanceof OverloadArmorItem) || !(b.getItem() instanceof OverloadArmorItem)) {
            return false;
        }
        var idA = OverloadArmorState.getArmorId(a);
        var idB = OverloadArmorState.getArmorId(b);
        return idA != null && idA.equals(idB);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            net.minecraft.world.item.Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        Level level = context.level();
        if (level == null) {
            return;
        }

        var snapshot = OverloadArmorState.snapshot(stack, level.registryAccess(), false);
        tooltipComponents.add(Component.translatable(
                "ae2lt.overload_armor.tooltip.overload",
                snapshot.currentLoad(),
                snapshot.baseOverload()));
        tooltipComponents.add(Component.translatable(
                "ae2lt.overload_armor.tooltip.energy",
                snapshot.storedEnergy(),
                snapshot.bufferCapacity()));

        if (snapshot.locked()) {
            tooltipComponents.add(Component.translatable(
                    "ae2lt.overload_armor.tooltip.locked",
                    snapshot.lockedTicks() / 20));
        } else if (snapshot.unpaidEnergy() > 0) {
            tooltipComponents.add(Component.translatable(
                    "ae2lt.overload_armor.tooltip.debt",
                    snapshot.unpaidEnergy(),
                    snapshot.debtTicks(),
                    OverloadArmorState.LOCK_TRIGGER_TICKS));
        } else {
            tooltipComponents.add(Component.translatable(
                    "ae2lt.overload_armor.tooltip.tip"));
        }
        tooltipComponents.add(Component.translatable("ae2lt.overload_armor.tooltip.workbench"));
    }

    public static boolean openEquippedMenu(Player player) {
        var carrier = OverloadArmorCarrierLocator.findFirstEquipped(
                player,
                stack -> stack.getItem() instanceof OverloadArmorItem);
        if (carrier == null) {
            return false;
        }

        var stack = carrier.armorStack();
        var armorId = OverloadArmorState.ensureArmorId(stack);
        MenuOpener.open(
                OverloadArmorMenu.TYPE,
                player,
                new OverloadArmorMenuLocator(armorId, carrier.locator()));
        return true;
    }

    private static void syncEquipmentState(Player player, ItemStack stack, boolean equipped) {
        if (!(stack.getItem() instanceof OverloadArmorItem)) {
            return;
        }

        OverloadArmorState.ensureArmorId(stack);
        var level = player.level();
        var dist = resolveDist(level);
        if (equipped) {
            OverloadArmorState.reconcileInstalledSubmodules(player, stack, level.registryAccess(), dist);
        }
        OverloadArmorState.syncSubmoduleActiveState(
                player,
                stack,
                level.registryAccess(),
                equipped,
                dist);
    }

    private static Dist resolveDist(Level level) {
        return level.isClientSide() ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }
}
