package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;

public class OverloadedPowerSupplyBlock extends AEBaseEntityBlock<OverloadedPowerSupplyBlockEntity> {

    public OverloadedPowerSupplyBlock() {
        super(metalProps());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        var be = this.getBlockEntity(level, pos);
        if (be != null) {
            if (!level.isClientSide()) {
                be.openMenu(player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}
