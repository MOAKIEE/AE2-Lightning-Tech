package com.moakiee.ae2lt.logic;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

/**
 * Output filter used by auto-return to decide whether a machine stack belongs
 * to one of the patterns loaded in the provider.
 * <p>
 * STRICT outputs are matched by exact {@link AEKey}. ID_ONLY outputs are
 * matched by item id only.
 */
public final class AllowedOutputFilter {
    private final Set<AEKey> strictOutputs = new LinkedHashSet<>();
    private final Set<ResourceLocation> idOnlyItemIds = new LinkedHashSet<>();

    public void allowStrict(AEKey key) {
        Objects.requireNonNull(key, "key");
        strictOutputs.add(key);
    }

    public void allowIdOnly(ResourceLocation itemId) {
        Objects.requireNonNull(itemId, "itemId");
        idOnlyItemIds.add(itemId);
    }

    public boolean isEmpty() {
        return strictOutputs.isEmpty() && idOnlyItemIds.isEmpty();
    }

    public boolean matches(AEKey key) {
        Objects.requireNonNull(key, "key");
        if (strictOutputs.contains(key)) {
            return true;
        }

        return key instanceof AEItemKey itemKey && idOnlyItemIds.contains(itemKey.getId());
    }

    @Override
    public String toString() {
        return "AllowedOutputFilter[strict=" + strictOutputs + ", idOnly=" + idOnlyItemIds + "]";
    }
}
