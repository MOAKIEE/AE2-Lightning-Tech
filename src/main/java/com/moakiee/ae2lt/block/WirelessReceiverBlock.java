package com.moakiee.ae2lt.block;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.block.AEBaseEntityBlock;

import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.item.WirelessLinkToolItem;

/**
 * Wireless receiver block. Place at a remote location and bind to a transmitter
 * using the wireless link tool to create a virtual network bridge.
 * Auto-links on placement if the player carries a bound link tool.
 */
public class WirelessReceiverBlock extends AEBaseEntityBlock<WirelessReceiverBlockEntity> {

    public WirelessReceiverBlock() {
        super(metalProps());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) return;

        if (level.getBlockEntity(pos) instanceof WirelessReceiverBlockEntity receiver) {
            UUID boundId = WirelessLinkToolItem.findBoundIdInInventory(player);
            if (boundId != null) {
                receiver.bindToTransmitter(boundId);
            }
        }
    }
}
