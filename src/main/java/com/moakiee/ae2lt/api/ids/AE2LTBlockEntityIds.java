package com.moakiee.ae2lt.api.ids;

import net.minecraft.resources.Identifier;

/**
 * Frozen registry IDs for the public-facing block entities of AE2 Lightning Tech.
 *
 * <p>These constants are part of the API contract. The mod will not change the
 * registered ID of any of these block entities without a major version bump.
 *
 * <p>Note the mismatch on the simulation room: the Java class is named
 * {@code LightningSimulationChamberBlockEntity} but its registered path is
 * {@code lightning_simulation_room}. The constant follows the registered path.
 */
public final class AE2LTBlockEntityIds {

    /**
     * Mod id of AE2 Lightning Tech. Frozen as part of the API contract.
     */
    public static final String MOD_ID = "ae2lt";

    public static final Identifier LIGHTNING_COLLECTOR =
            Identifier.fromNamespaceAndPath(MOD_ID, "lightning_collector");

    public static final Identifier LIGHTNING_SIMULATION_ROOM =
            Identifier.fromNamespaceAndPath(MOD_ID, "lightning_simulation_room");

    public static final Identifier LIGHTNING_ASSEMBLY_CHAMBER =
            Identifier.fromNamespaceAndPath(MOD_ID, "lightning_assembly_chamber");

    public static final Identifier OVERLOAD_PROCESSING_FACTORY =
            Identifier.fromNamespaceAndPath(MOD_ID, "overload_processing_factory");

    public static final Identifier TESLA_COIL =
            Identifier.fromNamespaceAndPath(MOD_ID, "tesla_coil");

    public static final Identifier CRYSTAL_CATALYZER =
            Identifier.fromNamespaceAndPath(MOD_ID, "crystal_catalyzer");

    private AE2LTBlockEntityIds() {
    }
}
