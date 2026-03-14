package com.moakiee.ae2lt.overload.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.overload.model.MatchMode;

/**
 * AE2-facing runtime implementation of an overload pattern.
 * <p>
 * It preserves the original pattern's execution behavior while overriding input
 * matching semantics per slot for planning and crafting extraction.
 */
public final class Ae2OverloadPatternDetails implements IPatternDetails, OverloadedProviderOnlyPatternDetails {
    private final AEItemKey definition;
    private final OverloadPatternDetails overloadDetails;
    private final IPatternDetails sourceDetails;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    public Ae2OverloadPatternDetails(AEItemKey definition,
                                     OverloadPatternDetails overloadDetails,
                                     IPatternDetails sourceDetails) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.overloadDetails = Objects.requireNonNull(overloadDetails, "overloadDetails");
        this.sourceDetails = Objects.requireNonNull(sourceDetails, "sourceDetails");

        var sourceInputs = sourceDetails.getInputs();
        if (sourceInputs.length != overloadDetails.inputs().size()) {
            throw new IllegalArgumentException("input slot count mismatch between source and overload details");
        }

        this.inputs = new IInput[sourceInputs.length];
        for (int slot = 0; slot < sourceInputs.length; slot++) {
            this.inputs[slot] = new OverloadInput(sourceInputs[slot], overloadDetails.inputs().get(slot).matchMode());
        }
        this.outputs = List.copyOf(sourceDetails.getOutputs());
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return sourceDetails.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        sourceDetails.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return sourceDetails.getTooltip(level, flags);
    }

    @Override
    public PatternExecutionHostKind requiredHostKind() {
        return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
    }

    @Override
    public String overloadPatternIdentity() {
        return overloadDetails.overloadPatternIdentity();
    }

    @Override
    public OverloadPatternDetails overloadPatternDetailsView() {
        return overloadDetails;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Ae2OverloadPatternDetails other && definition.equals(other.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    private static final class OverloadInput implements IInput {
        private final IInput sourceInput;
        private final MatchMode matchMode;
        private final GenericStack[] possibleInputs;
        private final List<AEItemKey> itemKeys;

        private OverloadInput(IInput sourceInput, MatchMode matchMode) {
            this.sourceInput = sourceInput;
            this.matchMode = matchMode;
            this.possibleInputs = sourceInput.getPossibleInputs();
            this.itemKeys = collectItemKeys(possibleInputs);
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return sourceInput.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return switch (matchMode) {
                case STRICT -> sourceInput.isValid(input, level);
                case ID_ONLY -> matchesItemId(input);
            };
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            var direct = sourceInput.getRemainingKey(template);
            if (direct != null || matchMode == MatchMode.STRICT) {
                return direct;
            }

            if (template instanceof AEItemKey itemKey) {
                for (var possible : itemKeys) {
                    if (possible.getItem() == itemKey.getItem()) {
                        var remaining = sourceInput.getRemainingKey(possible);
                        if (remaining != null) {
                            return remaining;
                        }
                    }
                }
            }
            return null;
        }

        private boolean matchesItemId(AEKey input) {
            if (!(input instanceof AEItemKey itemKey)) {
                return false;
            }
            for (var possible : itemKeys) {
                if (possible.getItem() == itemKey.getItem()) {
                    return true;
                }
            }
            return false;
        }

        private static List<AEItemKey> collectItemKeys(GenericStack[] possibleInputs) {
            var result = new ArrayList<AEItemKey>(possibleInputs.length);
            for (var possible : possibleInputs) {
                if (possible.what() instanceof AEItemKey itemKey) {
                    result.add(itemKey);
                }
            }
            if (result.isEmpty()) {
                throw new IllegalArgumentException("overload patterns currently only support item inputs");
            }
            return List.copyOf(result);
        }
    }
}
