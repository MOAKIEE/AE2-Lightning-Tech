package com.moakiee.ae2lt.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import com.moakiee.ae2lt.api.lightning.ILightningEnergyHandler;

/**
 * Public AE2 Lightning Tech capabilities.
 *
 * <p>The {@link ResourceLocation} used to identify this capability is part of
 * the frozen API contract:
 * <ul>
 *   <li>{@code ae2lt:lightning_energy} for {@link #LIGHTNING_ENERGY}</li>
 * </ul>
 *
 * <p>Note this ID uses this mod's own namespace ({@code ae2lt}). It is
 * deliberately not the same as any third-party bridging library's ID; addons that
 * want to use this mod's first-party API must query this capability, not the
 * library's.
 *
 * <p>The capability must be registered during common setup via
 * {@code CapabilityManager.registerCapabilities(ILightningEnergyHandler.class)}.
 */
public final class AE2LTCapabilities {

    public static Capability<ILightningEnergyHandler> LIGHTNING_ENERGY = CapabilityManager.get(new CapabilityToken<>(){});

    public static final ResourceLocation LIGHTNING_ENERGY_RL =
            new ResourceLocation("ae2lt", "lightning_energy");

    private AE2LTCapabilities() {
    }
}
