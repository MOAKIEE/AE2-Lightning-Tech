package com.moakiee.ae2lt.block;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import com.moakiee.ae2lt.blockentity.WirelessIdBlockEntity;

import java.util.List;

public class WirelessIdBlock extends Block implements EntityBlock {

    private static final String TAG_WIRELESS_ID = "WirelessId";

    public WirelessIdBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessIdBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        if (level.getBlockEntity(pos) instanceof WirelessIdBlockEntity be) {
            UUID existingId = getIdFromStack(stack);
            be.initializeId(existingId);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                        net.minecraft.world.entity.player.Player player) {
        if (level.getBlockEntity(pos) instanceof WirelessIdBlockEntity be) {
            be.onRemoved();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof WirelessIdBlockEntity idBe && idBe.getWirelessId() != null) {
            for (ItemStack drop : drops) {
                if (drop.getItem() == this.asItem()) {
                    writeIdToStack(drop, idBe.getWirelessId());
                }
            }
        }
        return drops;
    }

    // ── UUID helpers for ItemStack ──

    public static void writeIdToStack(ItemStack stack, UUID uuid) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(TAG_WIRELESS_ID, uuid);
        });
    }

    @Nullable
    public static UUID getIdFromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(TAG_WIRELESS_ID)) {
            return tag.getUUID(TAG_WIRELESS_ID);
        }
        return null;
    }

    public static void appendIdTooltip(ItemStack stack, List<Component> tooltip) {
        UUID id = getIdFromStack(stack);
        if (id != null) {
            String shortId = id.toString().substring(0, 8);
            tooltip.add(Component.translatable("ae2lt.wireless_id.bound", shortId)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("ae2lt.wireless_id.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
