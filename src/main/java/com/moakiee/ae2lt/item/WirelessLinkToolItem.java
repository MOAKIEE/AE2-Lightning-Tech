package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;

/**
 * Tool for binding wireless controllers and receivers.
 * <ol>
 *   <li>Right-click a Wireless Controller → store the transmitter UUID in the tool</li>
 *   <li>Right-click a Wireless Receiver → bind it to the stored UUID</li>
 *   <li>Right-click air → clear the stored UUID</li>
 * </ol>
 */
public class WirelessLinkToolItem extends Item {

    private static final String TAG_BOUND_ID = "BoundTransmitterId";

    public WirelessLinkToolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        var level = context.getLevel();
        var pos = context.getClickedPos();
        var stack = context.getItemInHand();

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        var be = level.getBlockEntity(pos);

        if (be instanceof WirelessOverloadedControllerBlockEntity wirelessCtrl) {
            UUID id = wirelessCtrl.getCardUUID();
            if (id == null) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.link_tool.no_card_in_controller")
                                .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            setBoundId(stack, id);
            String shortId = id.toString().substring(0, 8);
            player.displayClientMessage(
                    Component.translatable("ae2lt.link_tool.bound", shortId)
                            .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }

        if (be instanceof WirelessReceiverBlockEntity receiver) {
            UUID id = getBoundId(stack);
            if (id == null) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.link_tool.no_transmitter")
                                .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            receiver.bindToTransmitter(id);
            String shortId = id.toString().substring(0, 8);
            player.displayClientMessage(
                    Component.translatable("ae2lt.link_tool.linked", shortId,
                            pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.pass(stack);

        if (hasBoundId(stack)) {
            clearBoundId(stack);
            player.displayClientMessage(
                    Component.translatable("ae2lt.link_tool.cleared")
                            .withStyle(ChatFormatting.GREEN), true);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        UUID id = getBoundId(stack);
        if (id != null) {
            String shortId = id.toString().substring(0, 8);
            tooltip.add(Component.translatable("ae2lt.link_tool.tooltip.bound", shortId)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("ae2lt.link_tool.tooltip.empty")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ── UUID Storage ──

    public static void setBoundId(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(TAG_BOUND_ID, id);
        });
    }

    @Nullable
    public static UUID getBoundId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(TAG_BOUND_ID) ? tag.getUUID(TAG_BOUND_ID) : null;
    }

    public static boolean hasBoundId(ItemStack stack) {
        return getBoundId(stack) != null;
    }

    public static void clearBoundId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_BOUND_ID + "Most");
        tag.remove(TAG_BOUND_ID + "Least");
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /**
     * Check a player's inventory for a bound wireless link tool and return its UUID.
     * Used by {@link WirelessReceiverBlockEntity} auto-linking on placement.
     */
    @Nullable
    public static UUID findBoundIdInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (invStack.getItem() instanceof WirelessLinkToolItem) {
                UUID id = getBoundId(invStack);
                if (id != null) return id;
            }
        }
        return null;
    }
}
