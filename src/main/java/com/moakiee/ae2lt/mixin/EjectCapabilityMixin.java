package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import com.moakiee.ae2lt.logic.EjectModeRegistry;

/**
 * Intercepts {@link BlockEntity#getCapability} to proxy capability queries
 * at eject-mode adjacent positions back to the pattern provider's own position.
 * <p>
 * When the provider's chunk is loaded, queries are proxied to the provider
 * via its registered capabilities (normal path).
 * <p>
 * When the provider's chunk is NOT loaded but a persistent registration exists,
 * returns a rejecting handler that refuses all inserts, preventing the machine
 * from pushing products to the wrong target.
 */
@Mixin(BlockEntity.class)
public abstract class EjectCapabilityMixin {

    private static boolean ae2lt$proxying = false;

    @Unique
    private static final IItemHandler REJECTING_ITEM_HANDLER = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    };

    @Unique
    private static final IFluidHandler REJECTING_FLUID_HANDLER = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, IFluidHandler.FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) { return FluidStack.EMPTY; }
    };

    @SuppressWarnings("unchecked")
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void ae2lt$interceptEjectCapability(Capability<T> cap, Direction face,
            CallbackInfoReturnable<LazyOptional<T>> cir) {
        if (ae2lt$proxying) return;
        if (EjectModeRegistry.isBypassed()) return;

        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel)) return;

        BlockPos pos = self.getBlockPos();
        var entry = EjectModeRegistry.lookupByFace(level.dimension(), pos.asLong(), face);
        if (entry == null) return;

        var host = entry.getHost();

        if (host != null) {
            Level hostLevel = host.getLevel();
            if (hostLevel == null) return;
            BlockPos hostPos = host.getBlockPos();

            ae2lt$proxying = true;
            try {
                LazyOptional<T> result = host.getCapability(cap, face);
                if (result.isPresent()) {
                    cir.setReturnValue(result);
                }
            } finally {
                ae2lt$proxying = false;
            }
        } else {
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                cir.setReturnValue(LazyOptional.of(() -> (T) REJECTING_ITEM_HANDLER));
            } else if (cap == ForgeCapabilities.FLUID_HANDLER) {
                cir.setReturnValue(LazyOptional.of(() -> (T) REJECTING_FLUID_HANDLER));
            }
        }
    }
}
