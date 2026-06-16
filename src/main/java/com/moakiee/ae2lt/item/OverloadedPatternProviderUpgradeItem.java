package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.blockentity.ExtendedOverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

import appeng.blockentity.AEBaseBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class OverloadedPatternProviderUpgradeItem extends Item {

    public OverloadedPatternProviderUpgradeItem(Properties properties) {
        super(properties);
    }

    public static boolean canUpgrade(Level level, BlockPos pos) {
        var originalState = level.getBlockState(pos);
        if (!originalState.is(ModBlocks.OVERLOADED_PATTERN_PROVIDER.get())) {
            return false;
        }

        var blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof OverloadedPatternProviderBlockEntity
                && !(blockEntity instanceof ExtendedOverloadedPatternProviderBlockEntity);
    }

    public static void upgrade(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide() || !canUpgrade(level, pos)) {
            return;
        }

        var blockEntity = level.getBlockEntity(pos);
        var originalState = level.getBlockState(pos);
        var replacementState = copySharedProperties(
                originalState,
                ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());

        var replacementEntity = new ExtendedOverloadedPatternProviderBlockEntity(pos, replacementState);
        replaceBlockEntity(level, pos, blockEntity, replacementEntity, replacementState);
        stack.shrink(1);
    }

    private static void replaceBlockEntity(Level level, BlockPos pos, BlockEntity oldEntity,
                                           BlockEntity replacementEntity, BlockState replacementState) {
        var savedTag = oldEntity.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(pos);
        level.removeBlock(pos, false);
        level.setBlock(pos, replacementState, Block.UPDATE_ALL);
        level.setBlockEntity(replacementEntity);
        replacementEntity.loadWithComponents(savedTag, level.registryAccess());
        if (replacementEntity instanceof AEBaseBlockEntity aeBlockEntity) {
            aeBlockEntity.markForUpdate();
        } else {
            replacementEntity.setChanged();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState copySharedProperties(BlockState originalState, BlockState replacementState) {
        var state = replacementState;
        for (var entry : originalState.getValues().entrySet()) {
            Property property = entry.getKey();
            if (state.hasProperty(property)) {
                state = state.setValue(property, (Comparable) entry.getValue());
            }
        }
        return state;
    }
}
