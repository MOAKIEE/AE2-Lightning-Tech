package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

public class OverloadedInterfaceBlock extends AEBaseEntityBlock<OverloadedInterfaceBlockEntity> {

    public OverloadedInterfaceBlock(Properties properties) {
        super(metalProps(properties).forceSolidOn());
    }

    public OverloadedInterfaceBlock() {
        this(Properties.of());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        var be = this.getBlockEntity(level, pos);
        if (be != null) {
            if (!level.isClientSide()) {
                be.openMenu(player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
