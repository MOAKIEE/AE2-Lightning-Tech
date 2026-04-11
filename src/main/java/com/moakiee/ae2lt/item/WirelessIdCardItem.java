package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Wireless ID Card: stores a persistent UUID for pairing wireless controllers
 * and receivers. Crafted without a UUID; right-click in air to generate one.
 */
public class WirelessIdCardItem extends Item {

    private static final String TAG_WIRELESS_ID = "WirelessId";

    public WirelessIdCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);

        UUID existing = getCardId(stack);
        if (existing != null) {
            String shortId = existing.toString().substring(0, 8);
            player.displayClientMessage(
                    Component.translatable("ae2lt.wireless_id_card.already_bound", shortId)
                            .withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.success(stack);
        }

        UUID newId = UUID.randomUUID();
        setCardId(stack, newId);
        String shortId = newId.toString().substring(0, 8);
        player.displayClientMessage(
                Component.translatable("ae2lt.wireless_id_card.generated", shortId)
                        .withStyle(ChatFormatting.GREEN), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        UUID id = getCardId(stack);
        if (id != null) {
            String shortId = id.toString().substring(0, 8);
            tooltip.add(Component.translatable("ae2lt.wireless_id_card.bound", shortId)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("ae2lt.wireless_id_card.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean hasCardId(ItemStack stack) {
        return getCardId(stack) != null;
    }

    @Nullable
    public static UUID getCardId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(TAG_WIRELESS_ID) ? tag.getUUID(TAG_WIRELESS_ID) : null;
    }

    public static void setCardId(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(TAG_WIRELESS_ID, id);
        });
    }
}
