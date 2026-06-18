package com.moakiee.ae2lt.integration.ae2wtlib;

import com.moakiee.ae2lt.registry.ModItems;

import de.mari_023.ae2wtlib.api.registration.UpgradeHelper;

/**
 * Soft-optional ae2wtlib integration entry point.
 *
 * <p>This class references ae2wtlib API types directly, so it must only be
 * class-loaded when ae2wtlib is present.</p>
 */
public final class Ae2wtlibIntegration {

    private Ae2wtlibIntegration() {
    }

    public static void register() {
        UpgradeHelper.addUpgradeToAllTerminals(ModItems.OVERLOADED_FREQUENCY_CARD.get(), 1);
    }
}
