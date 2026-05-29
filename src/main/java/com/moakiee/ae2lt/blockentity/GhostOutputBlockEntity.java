package com.moakiee.ae2lt.blockentity;

import java.lang.ref.WeakReference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEItemKey;

import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;
import com.moakiee.ae2lt.registry.ModBlockEntities;

/**
 * Lightweight runtime-only BlockEntity returned by the getBlockEntity Mixin
 * at eject-mode interception positions (M.relative(F)).
 * <p>
 * Not persisted, not associated with any chunk. Its purpose is to:
 * <ol>
 *   <li>Satisfy {@code level.getBlockEntity(pos) != null} checks that some
 *       machines perform before querying capabilities.</li>
 *   <li>Expose an insert-only {@link IItemHandler} capability that proxies
 *       items into the host Pattern Provider's ME network.</li>
 * </ol>
 */
public class GhostOutputBlockEntity extends BlockEntity {

    @Nullable
    private WeakReference<OverloadedPatternProviderLogic> hostLogicRef;

    private final LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(this::createItemHandler);

    public GhostOutputBlockEntity(BlockPos pos) {
        super(ModBlockEntities.GHOST_OUTPUT.get(), pos, Blocks.AIR.defaultBlockState());
    }

    /**
     * Set the host logic that this ghost BE proxies item inserts to.
     * Called by {@link OverloadedPatternProviderLogic#refreshEjectRegistrations()}.
     */
    public void setHostLogic(@Nullable OverloadedPatternProviderLogic logic) {
        this.hostLogicRef = logic != null ? new WeakReference<>(logic) : null;
    }

    @Nullable
    private OverloadedPatternProviderLogic getHostLogic() {
        return hostLogicRef != null ? hostLogicRef.get() : null;
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCap.invalidate();
    }

    private IItemHandler createItemHandler() {
        return new EjectItemHandler();
    }

    /**
     * Insert-only item handler that proxies items into the host pattern
     * provider's ME network via eject-mode.
     */
    private class EjectItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            var logic = getHostLogic();
            if (logic == null) return stack;

            var key = AEItemKey.of(stack);
            if (key == null) return stack;

            if (simulate) {
                // For simulate, just check if the network can accept any amount
                long affordable = logic.maxAffordableExternalReturn(key, stack.getCount());
                if (affordable <= 0) return stack;
                if (affordable >= stack.getCount()) return ItemStack.EMPTY;
                return stack.split((int) affordable);
            }

            long remaining = logic.ejectInsertToNetwork(key, stack.getCount());
            if (remaining <= 0) return ItemStack.EMPTY;
            return stack.split((int) remaining);
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY; // insert-only
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return getHostLogic() != null;
        }
    }
}
