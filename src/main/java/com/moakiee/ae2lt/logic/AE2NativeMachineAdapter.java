package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;

/**
 * Built-in fallback {@link MachineAdapter} that handles any target reachable
 * through AE2's native APIs:
 * <ol>
 *   <li>{@link ICraftingMachine} — dedicated crafting machines (all-or-nothing plans).</li>
 *   <li>{@link PatternProviderTarget} — generic inventories via ME-storage or
 *       platform external-storage capabilities.</li>
 * </ol>
 * <p>
 * Registered as the lowest-priority (last) adapter in {@link MachineAdapterRegistry}.
 * Stateless singleton — all world context is passed through method parameters.
 */
final class AE2NativeMachineAdapter implements MachineAdapter {

    static final AE2NativeMachineAdapter INSTANCE = new AE2NativeMachineAdapter();

    private AE2NativeMachineAdapter() {}

    // ---- supports ---------------------------------------------------------------

    @Override
    public boolean supports(ServerLevel level, BlockPos pos) {
        // Accept any loaded position with a block entity.
        // Specific adapters registered before us can shadow this for blocks they own.
        return level.isLoaded(pos) && level.getBlockEntity(pos) != null;
    }

    // ---- canAccept --------------------------------------------------------------

    @Override
    public boolean canAccept(ServerLevel level, BlockPos pos, Direction face,
                             IPatternDetails pattern) {
        var machine = ICraftingMachine.of(level, pos, face);
        if (machine != null) {
            return machine.acceptsPlans();
        }
        return pattern.supportsPushInputsToExternalInventory();
    }

    // ---- pushCopies -------------------------------------------------------------

    @Override
    public PushResult pushCopies(ServerLevel level, BlockPos pos, Direction face,
                                 IPatternDetails pattern, KeyCounter[] inputs, int maxCopies,
                                 boolean blocking, Set<AEKey> patternInputs,
                                 IActionSource source) {
        // Current implementation: single-copy only (maxCopies is acknowledged but
        // we push at most 1 copy per call; multi-copy batching is future work).

        // 1. ICraftingMachine path — all-or-nothing
        var machine = ICraftingMachine.of(level, pos, face);
        if (machine != null && machine.acceptsPlans()) {
            if (machine.pushPattern(pattern, inputs, face)) {
                return new PushResult(1, List.of());
            }
            return PushResult.REJECTED;
        }

        // 2. Generic inventory path
        if (!pattern.supportsPushInputsToExternalInventory()) {
            return PushResult.REJECTED;
        }

        var be = level.getBlockEntity(pos);
        var target = PatternProviderTarget.get(level, pos, be, face, source);
        if (target == null) {
            return PushResult.REJECTED;
        }

        // Blocking mode: refuse if machine already holds inputs
        if (blocking && target.containsPatternInput(patternInputs)) {
            return PushResult.REJECTED;
        }

        // Simulate: can the target accept at least *some* of every input key?
        if (!adapterAcceptsAll(target, inputs)) {
            return PushResult.REJECTED;
        }

        // Commit — push items, collect overflow
        var overflow = new ArrayList<GenericStack>();
        pattern.pushInputsToExternalInventory(inputs, (what, amount) -> {
            var inserted = target.insert(what, amount, Actionable.MODULATE);
            if (inserted < amount) {
                overflow.add(new GenericStack(what, amount - inserted));
            }
        });

        return new PushResult(1, overflow);
    }

    // ---- helpers ----------------------------------------------------------------

    private static boolean adapterAcceptsAll(PatternProviderTarget target, KeyCounter[] inputHolder) {
        for (var inputList : inputHolder) {
            for (var input : inputList) {
                if (target.insert(input.getKey(), input.getLongValue(), Actionable.SIMULATE) == 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
