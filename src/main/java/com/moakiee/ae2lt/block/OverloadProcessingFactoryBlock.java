package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;

public class OverloadProcessingFactoryBlock extends AEBaseEntityBlock<OverloadProcessingFactoryBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public OverloadProcessingFactoryBlock() {
        super(metalProps().noOcclusion().forceSolidOn());
        registerDefaultState(defaultBlockState()
                .setValue(WORKING, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        var be = getBlockEntity(level, pos);
        if (be != null) {
            be.onNeighborChanged(fromPos);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.getItem() instanceof BucketItem) {
            if (useBucket(player, level, pos, heldItem, hand)) {
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }

        var be = getBlockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private boolean useBucket(Player player, Level level, BlockPos pos, ItemStack stack, InteractionHand hand) {
        var itemFluidOpt = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
        var be = level.getBlockEntity(pos);
        var blockFluid = be != null ? be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null) : null;
        if (!itemFluidOpt.isPresent() || blockFluid == null) {
            return false;
        }
        var itemFluid = itemFluidOpt.orElse(null);
        if (itemFluid == null) return false;

        int bucketVolume = 1000;

        if (itemFluid.getFluidInTank(0).isEmpty()) {
            var extracted = blockFluid.drain(bucketVolume, IFluidHandler.FluidAction.SIMULATE);
            if (extracted.isEmpty() || extracted.getAmount() != bucketVolume) {
                return false;
            }

            blockFluid.drain(bucketVolume, IFluidHandler.FluidAction.EXECUTE);
            itemFluid.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
            player.setItemInHand(hand, itemFluid.getContainer());

            playBucketSound(player, level, pos, extracted, true);
            return true;
        }

        var drained = itemFluid.drain(bucketVolume, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) {
            return false;
        }
        int inserted = blockFluid.fill(drained, IFluidHandler.FluidAction.SIMULATE);
        if (inserted != bucketVolume) {
            return false;
        }

        drained = itemFluid.drain(bucketVolume, IFluidHandler.FluidAction.EXECUTE);
        blockFluid.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        player.setItemInHand(hand, itemFluid.getContainer());
        playBucketSound(player, level, pos, drained, false);
        return true;
    }

    private void playBucketSound(Player player, Level level, BlockPos pos, FluidStack fluid, boolean fillBucket) {
        SoundEvent sound = fillBucket
                ? (fluid.getFluid().is(FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL)
                : (fluid.getFluid().is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY);
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return super.newBlockEntity(pos, state);
    }
}

