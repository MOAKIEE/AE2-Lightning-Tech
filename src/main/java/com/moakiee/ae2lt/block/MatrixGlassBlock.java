package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Matrix glass block. Carries a FORMED state so the client connected-texture
 * model can switch to the assembled appearance once the multiblock forms.
 * The controller updates this state on form/deform.
 */
public class MatrixGlassBlock extends MatrixMultiblockSimpleBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public MatrixGlassBlock(Properties properties, MatrixMultiblockComponent component) {
        super(properties, component);
        registerDefaultState(defaultBlockState().setValue(FORMED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }
}
