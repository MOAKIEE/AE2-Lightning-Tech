package com.moakiee.ae2lt.logic.energy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;

/**
 * Reflection-backed bridge for Applied Flux.
 * Keeps AE2LT's hard dependency surface limited to AE2 while still allowing
 * AppFlux-specific energy routing and FE cell inspection at runtime.
 */
public final class AppFluxBridge {

    private static final long UNLIMITED_TRANSFER_RATE_HINT = Long.MAX_VALUE;

    private static final ResourceLocation INDUCTION_CARD_ID =
            ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
    private static final String I_FLUX_CELL_CLASS = "com.glodblock.github.appflux.api.IFluxCell";
    private static final String FLUX_KEY_CLASS = "com.glodblock.github.appflux.common.me.key.FluxKey";
    private static final String ENERGY_TYPE_CLASS = "com.glodblock.github.appflux.common.me.key.type.EnergyType";
    private static final String ENERGY_CAP_CACHE_CLASS = "com.glodblock.github.appflux.common.me.energy.EnergyCapCache";
    private static final String ENERGY_HANDLER_CLASS = "com.glodblock.github.appflux.common.me.energy.EnergyHandler";
    private static final String AF_CONFIG_CLASS = "com.glodblock.github.appflux.config.AFConfig";

    @Nullable
    public static final AEKey FE_KEY;
    public static final long TRANSFER_RATE;

    @Nullable
    private static final Class<?> I_FLUX_CELL;
    @Nullable
    private static final Constructor<?> ENERGY_CAP_CACHE_CTOR;
    @Nullable
    private static final Method ENERGY_HANDLER_SEND;

    static {
        FE_KEY = resolveFeKey();
        TRANSFER_RATE = resolveTransferRate();
        I_FLUX_CELL = loadClass(I_FLUX_CELL_CLASS);

        var energyCapCacheClass = loadClass(ENERGY_CAP_CACHE_CLASS);
        ENERGY_CAP_CACHE_CTOR = energyCapCacheClass != null
                ? getConstructor(energyCapCacheClass, ServerLevel.class, BlockPos.class, Supplier.class)
                : null;
        ENERGY_HANDLER_SEND = resolveEnergyHandlerSend(energyCapCacheClass);
    }

    private AppFluxBridge() {}

    public static boolean isAvailable() {
        return FE_KEY != null && TRANSFER_RATE > 0;
    }

    public static boolean canUseEnergyHandler() {
        return isAvailable() && ENERGY_CAP_CACHE_CTOR != null && ENERGY_HANDLER_SEND != null;
    }

    @Nullable
    public static Item getInductionCard() {
        Item card = BuiltInRegistries.ITEM.get(INDUCTION_CARD_ID);
        return card != null && card != Items.AIR ? card : null;
    }

    public static boolean isInductionCard(Item item) {
        Item card = getInductionCard();
        return card != null && card == item;
    }

    public static boolean isFluxCell(ItemStack stack) {
        return !stack.isEmpty() && I_FLUX_CELL != null && I_FLUX_CELL.isInstance(stack.getItem());
    }

    public static long getFluxCellCapacity(ItemStack stack) {
        if (!isFluxCell(stack)) {
            return 0L;
        }

        var inventory = StorageCells.getCellInventory(stack, null);
        if (inventory != null) {
            Long maxEnergy = invokeLongMethod(inventory, "getMaxEnergy");
            if (maxEnergy != null) {
                return Math.max(0L, maxEnergy);
            }

            Long totalBytes = invokeLongMethod(inventory, "getTotalBytes");
            if (totalBytes != null) {
                return Math.max(0L, totalBytes);
            }
        }

        Long bytes = invokeLongMethod(stack.getItem(), "getBytes", stack);
        return bytes != null ? Math.max(0L, bytes) : 0L;
    }

    @Nullable
    public static Object createCapCache(ServerLevel level, BlockPos pos, Supplier<IGrid> gridSupplier) {
        if (!canUseEnergyHandler()) {
            return null;
        }

        try {
            return ENERGY_CAP_CACHE_CTOR.newInstance(level, pos, gridSupplier);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static long send(@Nullable Object energyCapCache, Direction side,
                            IStorageService storage, IActionSource source) {
        if (energyCapCache == null || ENERGY_HANDLER_SEND == null) {
            return 0L;
        }

        try {
            Object result = ENERGY_HANDLER_SEND.invoke(null, energyCapCache, side, storage, source);
            return result instanceof Number number ? number.longValue() : 0L;
        } catch (ReflectiveOperationException ignored) {
            return 0L;
        }
    }

    public static boolean hasEnergyCapability(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) != null;
    }

    @Nullable
    private static AEKey resolveFeKey() {
        try {
            Class<?> energyTypeClass = Class.forName(ENERGY_TYPE_CLASS);
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) energyTypeClass.asSubclass(Enum.class);
            Object feType = enumValueOf(enumClass, "FE");

            Class<?> fluxKeyClass = Class.forName(FLUX_KEY_CLASS);
            Method ofMethod = fluxKeyClass.getMethod("of", energyTypeClass);
            Object key = ofMethod.invoke(null, feType);
            return key instanceof AEKey aeKey ? aeKey : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Enum<?> enumValueOf(Class<? extends Enum<?>> enumClass, String name) {
        return Enum.valueOf((Class) enumClass, name);
    }

    private static long resolveTransferRate() {
        try {
            Class<?> configClass = Class.forName(AF_CONFIG_CLASS);
            Method method = configClass.getMethod("getFluxAccessorIO");
            Object result = method.invoke(null);
            if (result instanceof Number number) {
                long value = number.longValue();
                if (value == 0L) {
                    // Applied Flux uses 0 to mean "unlimited". We still need a
                    // positive hint for simulate/preload call sites, so do not
                    // treat it as bridge-unavailable.
                    return UNLIMITED_TRANSFER_RATE_HINT;
                }
                return Math.max(0L, value);
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return 0L;
    }

    @Nullable
    private static Method resolveEnergyHandlerSend(@Nullable Class<?> energyCapCacheClass) {
        if (energyCapCacheClass == null) {
            return null;
        }

        try {
            Class<?> handlerClass = Class.forName(ENERGY_HANDLER_CLASS);
            return handlerClass.getMethod(
                    "send",
                    energyCapCacheClass,
                    Direction.class,
                    IStorageService.class,
                    IActionSource.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Constructor<?> getConstructor(Class<?> owner, Class<?>... parameterTypes) {
        try {
            return owner.getConstructor(parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Long invokeLongMethod(Object target, String methodName, Object... args) {
        try {
            Class<?>[] parameterTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                parameterTypes[i] = args[i].getClass();
            }

            Method method = target.getClass().getMethod(methodName, parameterTypes);
            Object result = method.invoke(target, args);
            return result instanceof Number number ? number.longValue() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
