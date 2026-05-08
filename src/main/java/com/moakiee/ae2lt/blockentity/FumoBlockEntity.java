package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FumoBlockEntity extends BlockEntity {

    public static final float SPIN_DEGREES_PER_TICK = 6.0F;

    private static final String TAG_SPINNING = "Spinning";

    private boolean spinning;
    private float yRot;
    private float prevYRot;

    public FumoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUMO.get(), pos, state);
    }

    public boolean isSpinning() {
        return spinning;
    }

    public float getRenderYRot(float partialTick) {
        return prevYRot + (yRot - prevYRot) * partialTick;
    }

    public void toggleSpinning() {
        spinning = !spinning;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FumoBlockEntity be) {
        be.prevYRot = be.yRot;
        if (be.spinning) {
            be.yRot += SPIN_DEGREES_PER_TICK;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putBoolean(TAG_SPINNING, spinning);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        spinning = tag.getBooleanOr(TAG_SPINNING, false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean(TAG_SPINNING, spinning);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
