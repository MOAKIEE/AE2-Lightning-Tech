package com.moakiee.ae2lt.overload.pattern;

import net.minecraft.world.item.ItemStack;

/**
 * Boundary adapter that reparses a stored plain pattern stack back into a
 * host-neutral {@link ParsedPatternDefinition}.
 */
@FunctionalInterface
public interface PlainPatternResolver {
    ParsedPatternDefinition resolve(ItemStack sourcePatternStack);
}
