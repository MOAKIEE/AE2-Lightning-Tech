package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import appeng.util.InteractionUtil;

/**
 * A block that suppresses Minecraft block updates when the update chain
 * originates from a player action.
 * <p>
 * Right-click (empty hand): toggle enabled/disabled (state change uses
 * {@link Block#UPDATE_CLIENTS} | {@link Block#UPDATE_KNOWN_SHAPE} so
 * the toggle itself never triggers neighbour updates).
 * <p>
 * Shift + wrench right-click: remove the block silently without any
 * neighbour notifications.
 * <p>
 * When enabled, {@link #neighborChanged} uses {@link StackWalker} to
 * inspect the call stack; if {@code ServerGamePacketListenerImpl} is
 * present the update was player-initiated and a {@link RuntimeException}
 * is thrown, which the packet handler catches safely.
 */
public class UpdateSuppressorBlock extends Block {

    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    private static final String PACKET_LISTENER_CLASS =
            "net.minecraft.server.network.ServerGamePacketListenerImpl";

    public UpdateSuppressorBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(3.0F, 1200.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
        registerDefaultState(stateDefinition.any().setValue(ENABLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENABLED);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && state.getValue(ENABLED) && isInPlayerContext()) {
            throw new UpdateSuppressionException(pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            boolean nowEnabled = !state.getValue(ENABLED);
            level.setBlock(pos, state.setValue(ENABLED, nowEnabled),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.playSound(null, pos,
                    nowEnabled ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundSource.BLOCKS, 0.5F, nowEnabled ? 0.6F : 0.5F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!level.isClientSide && player.isShiftKeyDown() && InteractionUtil.canWrenchRotate(stack)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            Block.popResource(level, pos, new ItemStack(this));
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private static boolean isInPlayerContext() {
        return STACK_WALKER.walk(frames ->
                frames.anyMatch(frame -> PACKET_LISTENER_CLASS.equals(frame.getClassName()))
        );
    }

    public static class UpdateSuppressionException extends RuntimeException {
        public UpdateSuppressionException(BlockPos pos) {
            super("Update suppressed at " + pos.toShortString());
        }
    }
}
