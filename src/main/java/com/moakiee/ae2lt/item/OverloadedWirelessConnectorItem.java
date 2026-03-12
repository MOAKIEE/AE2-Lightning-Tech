package com.moakiee.ae2lt.item;

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
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;

/**
 * Overloaded Wireless Connector — a tool item for establishing / managing
 * wireless connections between an Overloaded Pattern Provider and remote machines.
 * <p>
 * Workflow:
 * <ol>
 *   <li>Shift + right-click an Overloaded Pattern Provider → select it (saved in item NBT).</li>
 *   <li>Shift + right-click a machine block face → create / update / disconnect connection.</li>
 *   <li>Right-click air → deselect the current Provider.</li>
 * </ol>
 */
public class OverloadedWirelessConnectorItem extends Item {

    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";

    public OverloadedWirelessConnectorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ── Shift + right-click on a block ──────────────────────────────────────

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        // Client side: return SUCCESS to consume the event and prevent use() from firing
        var level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        var pos = context.getClickedPos();
        var face = context.getClickedFace();
        var stack = context.getItemInHand();
        var state = level.getBlockState(pos);

        // Case 1: Shift + right-click on an Overloaded Pattern Provider → select it
        if (state.getBlock() instanceof OverloadedPatternProviderBlock) {
            var be = level.getBlockEntity(pos);
            if (be instanceof OverloadedPatternProviderBlockEntity provider
                    && provider.getProviderMode() == OverloadedPatternProviderBlockEntity.ProviderMode.NORMAL) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.connector.need_wireless")
                                .withStyle(ChatFormatting.GREEN),
                        true);
                return InteractionResult.FAIL;
            }
            selectProvider(stack, level, pos);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.selected",
                            pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResult.SUCCESS;
        }

        // Case 2: Shift + right-click on any other block → attempt to connect / update / disconnect
        if (!hasSelectedProvider(stack)) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.no_provider")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResult.FAIL;
        }

        var provider = getSelectedProvider(level, stack);
        if (provider == null) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.provider_lost")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            clearSelection(stack);
            return InteractionResult.FAIL;
        }

        // Target must have a BlockEntity (i.e. it is a "machine")
        var targetBe = level.getBlockEntity(pos);
        if (targetBe == null) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.not_machine")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResult.FAIL;
        }

        var targetDim = level.dimension();

        // Check existing connection to this machine
        var existing = provider.getConnections().stream()
                .filter(c -> c.sameTarget(targetDim, pos))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            if (existing.boundFace() == face) {
                // Same face → disconnect
                provider.removeConnection(targetDim, pos);
                player.displayClientMessage(
                        Component.translatable("ae2lt.connector.disconnected",
                                pos.getX(), pos.getY(), pos.getZ())
                                .withStyle(ChatFormatting.GREEN),
                        true);
            } else {
                // Different face → update bound face
                provider.addOrUpdateConnection(targetDim, pos, face);
                player.displayClientMessage(
                        Component.translatable("ae2lt.connector.updated",
                                pos.getX(), pos.getY(), pos.getZ(), face.getName())
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        } else {
            // New connection
            provider.addOrUpdateConnection(targetDim, pos, face);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.connected",
                            pos.getX(), pos.getY(), pos.getZ(), face.getName())
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }

        return InteractionResult.SUCCESS;
    }

    // ── Right-click air → deselect Provider ─────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }

        if (hasSelectedProvider(stack)) {
            clearSelection(stack);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.deselected")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    // ── NBT helpers (using DataComponents.CUSTOM_DATA) ────────────────────

    private void selectProvider(ItemStack stack, Level level, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var sel = new CompoundTag();
            sel.putString(TAG_DIM, level.dimension().location().toString());
            sel.putLong(TAG_POS, pos.asLong());
            tag.put(TAG_SELECTED, sel);
        });
    }

    private boolean hasSelectedProvider(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_SELECTED, CompoundTag.TAG_COMPOUND);
    }

    private void clearSelection(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_SELECTED);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /**
     * Resolve the selected Provider BlockEntity from the item's custom data.
     * Returns null if the provider is unloaded, missing, or wrong type.
     */
    private OverloadedPatternProviderBlockEntity getSelectedProvider(Level level, ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED)) {
            return null;
        }
        var sel = tag.getCompound(TAG_SELECTED);
        var dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(sel.getString(TAG_DIM)));
        var pos = BlockPos.of(sel.getLong(TAG_POS));

        // Resolve the target level (supports cross-dimension)
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
