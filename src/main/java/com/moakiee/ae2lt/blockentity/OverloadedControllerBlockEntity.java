package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.blockentity.networking.ControllerBlockEntity;

/**
 * Minimal custom controller node owner.
 * Extends AE2's controller block entity so future controller-scoped channel
 * changes can target this subtype without changing vanilla controller behavior.
 * <p>
 * Important: later 128-channel logic is keyed off this concrete owner type,
 * so vanilla ControllerBlockEntity instances remain untouched.
 */
public class OverloadedControllerBlockEntity extends ControllerBlockEntity implements OverloadedGridNodeOwner {
    private static final double INTERNAL_MAX_POWER = 16_000_000.0;

    public OverloadedControllerBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.OVERLOADED_CONTROLLER.get(), pos, blockState);
    }

    protected OverloadedControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.setInternalMaxPower(INTERNAL_MAX_POWER);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OverloadedControllerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }

        be.injectAEPower(AE2LTCommonConfig.overloadedControllerPassiveAePerTick(), Actionable.MODULATE);
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return null; // Controller uses AE power, not FE; no Forge EnergyStorage to expose
    }

    @Override
    protected IManagedGridNode createMainNode() {
        // AE2 15.x for 1.20.1 uses IManagedGridNode#setTagName here.
        return super.createMainNode()
                .setTagName("overloaded_controller")
                .setVisualRepresentation(ModBlocks.OVERLOADED_CONTROLLER.get());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        // AE2 network tool / network status uses the node's visual representation,
        // which defaults to this representative item in AENetworkedPoweredBlockEntity.
        // If this hook is renamed in another AE2/MC version, adjust accordingly.
        return ModBlocks.OVERLOADED_CONTROLLER.get().asItem();
    }

    // --- Forge Capability overrides ---

    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap,
            @org.jetbrains.annotations.Nullable Direction side) {
        return super.getCapability(cap, side);
    }
}
