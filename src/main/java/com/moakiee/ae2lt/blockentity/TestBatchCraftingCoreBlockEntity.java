package com.moakiee.ae2lt.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;

import com.moakiee.ae2lt.logic.craft.TestBatchCraftingCoreLogic;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class TestBatchCraftingCoreBlockEntity extends PatternProviderBlockEntity {
    public TestBatchCraftingCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.TEST_BATCH_CRAFTING_CORE.get(), pos, blockState);
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new TestBatchCraftingCoreLogic(getMainNode(), this);
    }

    @Override
    public TestBatchCraftingCoreLogic getLogic() {
        return (TestBatchCraftingCoreLogic) super.getLogic();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModBlocks.TEST_BATCH_CRAFTING_CORE.get());
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModBlocks.TEST_BATCH_CRAFTING_CORE.get());
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.TEST_BATCH_CRAFTING_CORE.get().asItem();
    }
}
