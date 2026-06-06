package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.network.FrequencyCardUsePacket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class OverloadedFrequencyCardItem extends Item {
    private static final String TAG_FREQUENCY_ID = "FrequencyId";
    private static final String TAG_AUTO_CONNECT = "AutoConnect";
    private static final String TAG_BOUND_CONTROLLER_DIM = "BoundControllerDim";
    private static final String TAG_BOUND_CONTROLLER_POS = "BoundControllerPos";
    private static final String TAG_OWNER_UUID = "OwnerUuid";

    public OverloadedFrequencyCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return handleBlockUse(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleBlockUse(context);
    }

    private InteractionResult handleBlockUse(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        var level = context.getLevel();
        if (level.isClientSide()) {
            Vec3 hit = context.getClickLocation();
            PacketDistributor.sendToServer(new FrequencyCardUsePacket(
                    context.getHand(),
                    context.getClickedPos(),
                    context.getClickedFace(),
                    hit.x,
                    hit.y,
                    hit.z,
                    net.minecraft.client.gui.screens.Screen.hasShiftDown()));
            return InteractionResult.SUCCESS_NO_ITEM_USED;
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            var data = getData(stack);
            if (data.isBound() && !data.canBeUsedBy(player.getUUID())) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.frequency_card.card_owner_mismatch")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResultHolder.fail(stack);
            }
            setData(stack, data.clearFrequency());
            player.displayClientMessage(
                    Component.translatable("ae2lt.frequency_card.cleared").withStyle(ChatFormatting.GREEN),
                    true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag tooltipFlag) {
        var data = getData(stack);
        if (data.isBound()) {
            tooltip.add(Component.translatable("tooltip.ae2lt.frequency_card.frequency", data.frequencyId())
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.ae2lt.frequency_card.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable(
                        data.autoConnect()
                                ? "tooltip.ae2lt.frequency_card.auto_on"
                                : "tooltip.ae2lt.frequency_card.auto_off")
                .withStyle(data.autoConnect() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));

        if (data.isBound()) {
            tooltip.add(Component.translatable("tooltip.ae2lt.frequency_card.bound_hint")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.ae2lt.frequency_card.bind_hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.ae2lt.frequency_card.controls")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
    }

    public static OverloadedFrequencyCardData getData(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return fromTag(tag);
    }

    public static void setData(ItemStack stack, OverloadedFrequencyCardData data) {
        var tag = toTag(data);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static void bindFrequency(
            ItemStack stack,
            int frequencyId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            UUID ownerUuid) {
        setData(stack, getData(stack).bindFrequency(
                frequencyId,
                dimension.location().toString(),
                pos.asLong(),
                ownerUuid));
    }

    public static boolean toggleAutoConnect(ItemStack stack) {
        var data = getData(stack).toggleAutoConnect();
        setData(stack, data);
        return data.autoConnect();
    }

    public static Optional<ItemStack> findAutoConnectCard(Player player) {
        UUID playerUuid = player.getUUID();
        var main = player.getMainHandItem();
        if (isUsableAutoCard(main, playerUuid)) {
            return Optional.of(main);
        }

        var offhand = player.getOffhandItem();
        if (isUsableAutoCard(offhand, playerUuid)) {
            return Optional.of(offhand);
        }

        ItemStack hotbarCandidate = ItemStack.EMPTY;
        int hotbarCount = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9 && slot < inventory.items.size(); slot++) {
            var stack = inventory.items.get(slot);
            if (isUsableAutoCard(stack, playerUuid)) {
                hotbarCandidate = stack;
                hotbarCount++;
            }
        }
        if (hotbarCount == 1) {
            return Optional.of(hotbarCandidate);
        }
        if (hotbarCount > 1) {
            return Optional.empty();
        }

        ItemStack backpackCandidate = ItemStack.EMPTY;
        int backpackCount = 0;
        for (int slot = 9; slot < inventory.items.size(); slot++) {
            var stack = inventory.items.get(slot);
            if (isUsableAutoCard(stack, playerUuid)) {
                backpackCandidate = stack;
                backpackCount++;
            }
        }
        return backpackCount == 1 ? Optional.of(backpackCandidate) : Optional.empty();
    }

    public static boolean hasMultipleAutoConnectCandidates(Player player) {
        UUID playerUuid = player.getUUID();
        int count = 0;
        if (isUsableAutoCard(player.getMainHandItem(), playerUuid)) count++;
        if (isUsableAutoCard(player.getOffhandItem(), playerUuid)) count++;
        for (var stack : player.getInventory().items) {
            if (isUsableAutoCard(stack, playerUuid)) {
                count++;
            }
        }
        return count > 1;
    }

    private static boolean isUsableAutoCard(ItemStack stack, UUID playerUuid) {
        if (!(stack.getItem() instanceof OverloadedFrequencyCardItem)) {
            return false;
        }
        var data = getData(stack);
        return data.isBound() && data.autoConnect() && data.canBeUsedBy(playerUuid);
    }

    private static OverloadedFrequencyCardData fromTag(CompoundTag tag) {
        int frequencyId = tag.contains(TAG_FREQUENCY_ID)
                ? tag.getInt(TAG_FREQUENCY_ID)
                : OverloadedFrequencyCardData.NO_FREQUENCY;
        boolean autoConnect = tag.getBoolean(TAG_AUTO_CONNECT);
        Optional<String> dim = tag.contains(TAG_BOUND_CONTROLLER_DIM)
                ? Optional.of(tag.getString(TAG_BOUND_CONTROLLER_DIM))
                : Optional.empty();
        Optional<Long> pos = tag.contains(TAG_BOUND_CONTROLLER_POS)
                ? Optional.of(tag.getLong(TAG_BOUND_CONTROLLER_POS))
                : Optional.empty();
        Optional<UUID> owner = Optional.empty();
        if (tag.contains(TAG_OWNER_UUID)) {
            try {
                owner = Optional.of(UUID.fromString(tag.getString(TAG_OWNER_UUID)));
            } catch (IllegalArgumentException ignored) {
                owner = Optional.empty();
            }
        }
        return new OverloadedFrequencyCardData(frequencyId, autoConnect, dim, pos, owner);
    }

    private static CompoundTag toTag(OverloadedFrequencyCardData data) {
        var tag = new CompoundTag();
        if (data.isBound()) {
            tag.putInt(TAG_FREQUENCY_ID, data.frequencyId());
        }
        if (data.autoConnect()) {
            tag.putBoolean(TAG_AUTO_CONNECT, true);
        }
        data.boundControllerDimension().ifPresent(value -> tag.putString(TAG_BOUND_CONTROLLER_DIM, value));
        data.boundControllerPos().ifPresent(value -> tag.putLong(TAG_BOUND_CONTROLLER_POS, value));
        data.ownerUuid().ifPresent(value -> tag.putString(TAG_OWNER_UUID, value.toString()));
        return tag;
    }
}
