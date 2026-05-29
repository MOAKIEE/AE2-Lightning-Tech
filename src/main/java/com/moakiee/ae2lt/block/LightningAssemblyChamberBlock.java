package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

public class LightningAssemblyChamberBlock extends AEBaseEntityBlock<LightningAssemblyChamberBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public LightningAssemblyChamberBlock() {
        super(metalProps().noOcclusion().forceSolidOn());
        registerDefaultState(defaultBlockState()
                .setValue(WORKING, false)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
        builder.add(POWERED);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.none();
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState currentState, LightningAssemblyChamberBlockEntity be) {
        return currentState.setValue(POWERED, be.isPowered());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, net.minecraft.core.BlockPos pos,
            Block block, net.minecraft.core.BlockPos fromPos, boolean isMoving) {
        var be = getBlockEntity(level, pos);
        if (be != null) {
            be.onNeighborChanged(fromPos);
        }
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        var be = getBlockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return super.newBlockEntity(pos, state);
    }
}
