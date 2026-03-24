package com.moakiee.ae2lt.grid;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Static holder for passing the correct {@link BlockEntityType} into
 * a third-party parent constructor that resolves the wrong type
 * (e.g. via GlodUtil cache).
 * <p>
 * Set before construction, read by the ExtAE constructor mixin,
 * cleared after construction. Single-threaded (server tick).
 */
public final class OverloadedBETypeOverride {

    public static volatile BlockEntityType<?> pending;

    private OverloadedBETypeOverride() {}
}
