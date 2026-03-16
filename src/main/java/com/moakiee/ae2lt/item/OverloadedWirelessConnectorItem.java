package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.network.WirelessConnectorUsePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A tool item for establishing and managing wireless connections between an
 * Overloaded Pattern Provider and remote machines.
 * <p>
 * Workflow:
 * <ol>
 *   <li>Right-click an Overloaded Pattern Provider to select it.</li>
 *   <li>Right-click a machine block face to create, update, or remove a connection.</li>
 *   <li>Right-click air to clear the current provider selection.</li>
 * </ol>
 */
public class OverloadedWirelessConnectorItem extends Item {

    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";

    public OverloadedWirelessConnectorItem(Properties properties) {
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
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var targetBe = level.getBlockEntity(pos);
        boolean isProvider = state.getBlock() instanceof OverloadedPatternProviderBlock;
        boolean isMachine = targetBe != null;

        if (!isProvider && !isMachine) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            PacketDistributor.sendToServer(new WirelessConnectorUsePacket(
                    context.getHand(),
                    pos,
                    context.getClickedFace(),
                    net.minecraft.client.gui.screens.Screen.hasControlDown()));
            return InteractionResult.SUCCESS_NO_ITEM_USED;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }

        if (hasSelectedProvider(stack)) {
            clearSelection(stack);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.deselected").withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    public static void selectProvider(ItemStack stack, Level level, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var sel = new CompoundTag();
            sel.putString(TAG_DIM, level.dimension().location().toString());
            sel.putLong(TAG_POS, pos.asLong());
            tag.put(TAG_SELECTED, sel);
        });
    }

    public static boolean hasSelectedProvider(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_SELECTED, CompoundTag.TAG_COMPOUND);
    }

    public static void clearSelection(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_SELECTED);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /**
     * Resolve the selected provider from the item's custom data.
     * Returns null if the provider is unloaded, missing, or the block entity type changed.
     */
    public static OverloadedPatternProviderBlockEntity getSelectedProvider(Level level, ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED)) {
            return null;
        }

        var sel = tag.getCompound(TAG_SELECTED);
        var dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(sel.getString(TAG_DIM)));
        var pos = BlockPos.of(sel.getLong(TAG_POS));

        Level targetLevel = level;
        if (!level.dimension().equals(dimKey) && level instanceof ServerLevel sl) {
            targetLevel = sl.getServer().getLevel(dimKey);
        }
        if (targetLevel == null || !targetLevel.isLoaded(pos)) {
            return null;
        }

        var be = targetLevel.getBlockEntity(pos);
        return be instanceof OverloadedPatternProviderBlockEntity provider ? provider : null;
    }
}
