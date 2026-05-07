package com.moakiee.ae2lt.api.ids;

import net.minecraft.resources.Identifier;

/**
 * Frozen registry IDs for the public-facing recipe types of AE2 Lightning Tech.
 *
 * <p>These constants are part of the API contract. The mod will not change the
 * registered ID of any of these recipe types without a major version bump.
 */
public final class AE2LTRecipeIds {

    private static final String MOD_ID = AE2LTBlockEntityIds.MOD_ID;

    public static final Identifier LIGHTNING_ASSEMBLY =
            Identifier.fromNamespaceAndPath(MOD_ID, "lightning_assembly");

    public static final Identifier LIGHTNING_TRANSFORM =
            Identifier.fromNamespaceAndPath(MOD_ID, "lightning_transform");

    public static final Identifier LIGHTNING_SIMULATION =
            Identifier.fromNamespaceAndPath(MOD_ID, "lightning_simulation");

    public static final Identifier OVERLOAD_PROCESSING =
            Identifier.fromNamespaceAndPath(MOD_ID, "overload_processing");

    public static final Identifier CRYSTAL_CATALYZER =
            Identifier.fromNamespaceAndPath(MOD_ID, "crystal_catalyzer");

    public static final Identifier LIGHTNING_STRIKE =
            Identifier.fromNamespaceAndPath(MOD_ID, "lightning_strike");

    private AE2LTRecipeIds() {
    }
}
