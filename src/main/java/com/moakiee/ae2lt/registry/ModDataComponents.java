package com.moakiee.ae2lt.registry;

/**
 * NBT tag keys used by AE2LT for item and memory-card data.
 *
 * In 1.20.1, item data is stored via NBT tags rather than Data Components.
 * The {@link #EXPORTED_MACHINE_CONFIG} key carries machine-specific
 * configuration written by a block entity's
 * {@code exportSettings(MEMORY_CARD, ...)} and read back by
 * {@code importSettings(MEMORY_CARD, ...)}.
 *
 * The schema of the inner CompoundTag is owned by each BE — there's no
 * cross-machine compatibility guarantee. Same-block copy/paste is the
 * only use case we care about.
 */
public final class ModDataComponents {
    private ModDataComponents() {}

    /** NBT key for machine-specific configuration on memory cards. */
    public static final String EXPORTED_MACHINE_CONFIG = "ae2lt:exported_machine_config";
}
