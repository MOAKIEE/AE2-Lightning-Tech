package com.moakiee.ae2lt.logic.timewheelcpu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

public interface TimeWheelCraftingCpuHost {
    boolean isCpuActive();

    @Nullable
    IGrid getGrid();

    IActionSource getActionSource();

    @Nullable
    Level getLevel();

    void markCpuDirty();
}
