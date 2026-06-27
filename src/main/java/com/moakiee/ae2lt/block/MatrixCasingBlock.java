package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Matrix casing block. Carries a FORMED state so its connected texture can
 * switch to the assembled shell appearance with the rest of the multiblock.
 */
public class MatrixCasingBlock extends MatrixMultiblockSimpleBlock {
    public static final BooleanProperty FORMED = MatrixGlassBlock.FORMED;

    public MatrixCasingBlock(Properties properties, MatrixMultiblockComponent component) {
        super(properties, component);
        registerDefaultState(defaultBlockState().setValue(FORMED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }
}
