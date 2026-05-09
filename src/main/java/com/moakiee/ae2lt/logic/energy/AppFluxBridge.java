package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

/**
 * Disabled Applied Flux bridge for the 26.1.2 port.
 * <p>
 * AppFlux does not have a 26.1.2 API artifact in this workspace. Keep this
 * facade so existing callers stay simple, but do not probe or bind AppFlux
 * runtime classes on this branch.
 */
public final class AppFluxBridge {

    @Nullable
    public static final AEKey FE_KEY = null;
    public static final long TRANSFER_RATE = 0L;

    private AppFluxBridge() {}

    public static boolean isAvailable() {
        return FE_KEY != null && TRANSFER_RATE > 0;
    }

    public static boolean canUseEnergyHandler() {
        return false;
    }

    @Nullable
    public static Item getInductionCard() {
        return null;
    }

    public static boolean isInductionCard(Item item) {
        return false;
    }

    public static boolean isFluxCell(ItemStack stack) {
        return false;
    }

    public static long getFluxCellCapacity(ItemStack stack) {
        return 0L;
    }

    public static void persistCellStorage(@Nullable MEStorage storage) {
    }

    @Nullable
    public static Object createCapCache(ServerLevel level, BlockPos pos,
                                        Supplier<IGrid> gridSupplier) {
        return null;
    }

    @Nullable
    public static TargetAccess resolveEnergyTarget(@Nullable Object energyCapCache, Direction side) {
        return null;
    }

    public static long simulateTarget(@Nullable TargetAccess target, long maxFe) {
        return 0L;
    }

    public static long sendToTarget(@Nullable TargetAccess target, IStorageService storage,
                                    IActionSource source, long maxFe) {
        return 0L;
    }

    public static long sendToTargetKnownDemand(@Nullable TargetAccess target, IStorageService storage,
                                               IActionSource source, long requested) {
        return 0L;
    }

    public static long sendToTargetKnownDemand(@Nullable TargetAccess target, BufferedMEStorage buffer,
                                               IActionSource source, long requested) {
        return 0L;
    }

    public static long sendToTargetRepeatedOptimistic(@Nullable TargetAccess target, BufferedMEStorage buffer,
                                                      IActionSource source, long maxFe, int maxCalls) {
        return 0L;
    }

    public static boolean hasEnergyCapability(ServerLevel level, BlockPos pos,
                                              Direction face) {
        return level.getCapability(Capabilities.Energy.BLOCK, pos, face) != null;
    }
}
