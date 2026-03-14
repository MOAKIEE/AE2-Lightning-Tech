package com.moakiee.ae2lt.overload.cpu;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.crafting.execution.CraftingCpuLogic;

/**
 * Runtime handle for the crafting CPU instance that owns an overload-side
 * pending-output state.
 * <p>
 * This keeps the state model explicit about ownership without requiring any
 * changes to AE2's native job objects.
 */
public final class OverloadCpuOwner {
    private final UUID craftingId;
    private final int logicIdentity;
    private final WeakReference<CraftingCpuLogic> logicRef;

    private OverloadCpuOwner(UUID craftingId, CraftingCpuLogic logic) {
        this.craftingId = Objects.requireNonNull(craftingId, "craftingId");
        this.logicIdentity = System.identityHashCode(logic);
        this.logicRef = new WeakReference<>(logic);
    }

    public static OverloadCpuOwner from(CraftingCpuLogic logic) {
        Objects.requireNonNull(logic, "logic");
        var link = logic.getLastLink();
        if (link == null) {
            throw new IllegalStateException("crafting logic has no active link");
        }
        return new OverloadCpuOwner(link.getCraftingID(), logic);
    }

    public UUID craftingId() {
        return craftingId;
    }

    public int logicIdentity() {
        return logicIdentity;
    }

    public @Nullable CraftingCpuLogic logic() {
        return logicRef.get();
    }

    @Override
    public String toString() {
        return "OverloadCpuOwner[craftingId=" + craftingId + ", logicIdentity=" + logicIdentity + "]";
    }
}
