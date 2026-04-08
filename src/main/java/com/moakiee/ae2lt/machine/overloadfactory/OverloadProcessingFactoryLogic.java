package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Optional;

import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.machine.common.AbstractGridRecipeMachineLogic;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingLockedRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCandidate;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeService;

public final class OverloadProcessingFactoryLogic extends AbstractGridRecipeMachineLogic<
        OverloadProcessingFactoryBlockEntity,
        OverloadProcessingLockedRecipe,
        OverloadProcessingRecipeCandidate> {
    public static final int MIN_PROCESS_TICKS = 5;

    public OverloadProcessingFactoryLogic(OverloadProcessingFactoryBlockEntity host) {
        super(host);
    }

    @Override
    protected int getMinProcessTicks() {
        return MIN_PROCESS_TICKS;
    }

    @Override
    protected long getMaxEnergyPerTickForSpeedCards(int speedCards) {
        return switch (speedCards) {
            case 0 -> 200_000L;
            case 1 -> 1_000_000L;
            case 2 -> 4_000_000L;
            case 3 -> 16_000_000L;
            default -> 64_000_000L;
        };
    }

    @Override
    protected long getTotalEnergy(OverloadProcessingLockedRecipe lockedRecipe) {
        return lockedRecipe.totalEnergy();
    }

    @Override
    protected Optional<OverloadProcessingRecipeCandidate> validateLockedRecipe(
            OverloadProcessingLockedRecipe lockedRecipe) {
        return OverloadProcessingRecipeService.findLockedRecipeMatch(
                host.getLevel(),
                host.getInventory(),
                host.getInputFluid(),
                host.getOutputFluid(),
                lockedRecipe,
                host.getAvailableHighVoltage(),
                host.getAvailableExtremeHighVoltage());
    }
}
