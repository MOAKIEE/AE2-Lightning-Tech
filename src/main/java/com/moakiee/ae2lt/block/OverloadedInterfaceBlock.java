package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

public class OverloadedInterfaceBlock extends AEBaseEntityBlock<OverloadedInterfaceBlockEntity> {

    public OverloadedInterfaceBlock() {
        super(metalProps().forceSolidOn());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hitResult) {
        var be = this.getBlockEntity(level, pos);
        if (be != null) {
            if (!level.isClientSide()) {
                be.openMenu(player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return super.newBlockEntity(pos, state);
    }
}
