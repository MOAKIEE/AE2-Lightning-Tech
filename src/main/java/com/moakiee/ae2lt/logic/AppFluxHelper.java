package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEKey;

/**
 * Shared helper for AppFlux (Applied Flux) integration.
 * Resolves the FE AEKey and transfer rate via reflection at class-load time.
 */
public final class AppFluxHelper {

    private static final ResourceLocation INDUCTION_CARD_ID =
            ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");

    @Nullable
    public static final AEKey FE_KEY;
    public static final int TRANSFER_RATE;

    static {
        AEKey resolvedKey = null;
        try {
            var energyTypeClass = Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) energyTypeClass.asSubclass(Enum.class);
            Object feType = Enum.valueOf(enumClass, "FE");

            var fluxKeyClass = Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey");
            var ofMethod = fluxKeyClass.getMethod("of", energyTypeClass);
            Object key = ofMethod.invoke(null, feType);
            resolvedKey = key instanceof AEKey aeKey ? aeKey : null;
        } catch (ReflectiveOperationException ignored) {
        }
        FE_KEY = resolvedKey;

        int resolvedRate = 0;
        try {
            var configClass = Class.forName("com.glodblock.github.appflux.config.AFConfig");
            var method = configClass.getMethod("getFluxAccessorIO");
            Object result = method.invoke(null);
            if (result instanceof Number num) {
                long value = num.longValue();
                if (value > 0) {
                    resolvedRate = (int) Math.min(Integer.MAX_VALUE, value);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        TRANSFER_RATE = resolvedRate;
    }

    public static boolean isAvailable() {
        return FE_KEY != null && TRANSFER_RATE > 0;
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

    private AppFluxHelper() {}
}
