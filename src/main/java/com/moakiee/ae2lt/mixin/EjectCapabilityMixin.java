package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;

import com.moakiee.ae2lt.logic.EjectModeRegistry;

/**
 * Intercepts {@link BlockCapability#getCapability} to proxy capability queries
 * at eject-mode adjacent positions back to the pattern provider's own position.
 * <p>
 * AE2's capability registration on the provider will then serve the
 * {@code UnlimitedReturnInventory} via its normal {@code GENERIC_INTERNAL_INV}
 * bridge to {@code IItemHandler}/{@code IFluidHandler}.
 */
@Mixin(BlockCapability.class)
public abstract class EjectCapabilityMixin<T, C> {

    private static final ThreadLocal<Boolean> PROXYING = ThreadLocal.withInitial(() -> false);

    @SuppressWarnings("unchecked")
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private void ae2lt$interceptEjectCapability(Level level, BlockPos pos,
            BlockState state, BlockEntity blockEntity, C context,
            CallbackInfoReturnable<T> cir) {
        if (PROXYING.get()) return;
        if (EjectModeRegistry.isBypassed()) return;
        if (!(level instanceof ServerLevel)) return;
        if (!(context instanceof Direction face)) return;

        var entry = EjectModeRegistry.lookupByFace(level.dimension(), pos.asLong(), face);
        if (entry == null) return;

        var provider = entry.providerRef().get();
        if (provider == null) return;

        Level providerLevel = provider.getLevel();
        if (providerLevel == null) return;
        BlockPos providerPos = provider.getBlockPos();
        BlockState providerState = providerLevel.getBlockState(providerPos);

        PROXYING.set(true);
        try {
            BlockCapability<T, C> cap = (BlockCapability<T, C>) (Object) this;
            T result = cap.getCapability(providerLevel, providerPos,
                    providerState, provider, context);
            if (result != null) {
                cir.setReturnValue(result);
            }
        } finally {
            PROXYING.set(false);
        }
    }
}
