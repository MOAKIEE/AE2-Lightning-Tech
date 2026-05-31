package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

public final class MatrixCraftingCluster {
    private final BooleanSupplier formed;
    private final List<MatrixPatternCore> patternCores;
    private final List<MatrixCraftCore> craftCores;
    private final MatrixHost host;
    private final CraftingCore engine;

    public MatrixCraftingCluster(BooleanSupplier formed,
                                 List<? extends MatrixPatternCore> patternCores,
                                 List<? extends MatrixCraftCore> craftCores,
                                 CraftingCoreHost host,
                                 CoreParams params,
                                 CopyAssembler assembler,
                                 CraftingCoreRegistry registry) {
        this.formed = Objects.requireNonNull(formed);
        this.patternCores = new ArrayList<>(patternCores);
        this.craftCores = new ArrayList<>(craftCores);
        this.host = new MatrixHost(Objects.requireNonNull(host));
        this.engine = new CraftingCore(this.host, params, assembler, registry);
    }

    public void addPatternCore(MatrixPatternCore core) {
        if (core != null && !patternCores.contains(core)) {
            patternCores.add(core);
        }
    }

    public void removePatternCore(MatrixPatternCore core) {
        patternCores.remove(core);
    }

    public void addCraftCore(MatrixCraftCore core) {
        if (core != null && !craftCores.contains(core)) {
            craftCores.add(core);
        }
    }

    public void removeCraftCore(MatrixCraftCore core) {
        craftCores.remove(core);
    }

    public List<IPatternDetails> getAvailablePatterns() {
        if (!formed.getAsBoolean()) return List.of();

        var seen = new IdentityHashMap<IPatternDetails, Boolean>();
        var result = new ArrayList<IPatternDetails>();
        for (var core : patternCores) {
            for (var pattern : core.getAvailablePatterns()) {
                if (pattern != null && !seen.containsKey(pattern)) {
                    seen.put(pattern, Boolean.TRUE);
                    result.add(pattern);
                }
            }
        }
        return List.copyOf(result);
    }

    public boolean hasPattern(IPatternDetails details) {
        if (!formed.getAsBoolean() || details == null) return false;
        for (var core : patternCores) {
            for (var pattern : core.getAvailablePatterns()) {
                if (pattern == details) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getBatchCapacity(IPatternDetails details) {
        if (!hasPattern(details)) return 0;
        return availableCapacity();
    }

    public int pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, int maxCraft) {
        if (!hasPattern(details)) return maxCraft;
        return engine.pushBatch(details, scaledInputs, maxCraft);
    }

    public boolean isBusy() {
        return !formed.getAsBoolean() || availableCapacity() <= 0;
    }

    public int availableCapacity() {
        if (!formed.getAsBoolean()) return 0;
        return engine.availableCapacity();
    }

    public int threadsInFlight() {
        return engine.threadsInFlight();
    }

    public void writeEngineTo(CompoundTag tag, HolderLookup.Provider registries) {
        engine.writeTo(tag, registries);
    }

    public void readEngineFrom(CompoundTag tag, HolderLookup.Provider registries) {
        engine.readFrom(tag, registries);
    }

    public int totalThreadCapacity() {
        if (!formed.getAsBoolean()) return 0;
        long total = 0;
        for (var core : craftCores) {
            int capacity = core.threadCapacity();
            if (capacity > 0) {
                total += capacity;
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }

    private final class MatrixHost implements CraftingCoreHost {
        private final CraftingCoreHost delegate;

        private MatrixHost(CraftingCoreHost delegate) {
            this.delegate = delegate;
        }

        @Override
        public long getGameTime() {
            return delegate.getGameTime();
        }

        @Override
        public int maxThreads() {
            return totalThreadCapacity();
        }

        @Override
        public boolean isRemoved() {
            return delegate.isRemoved();
        }

        @Override
        public boolean isConnected() {
            return formed.getAsBoolean() && delegate.isConnected();
        }

        @Override
        public double extractEnergy(double amount) {
            return delegate.extractEnergy(amount);
        }

        @Override
        public void injectEnergy(double amount) {
            delegate.injectEnergy(amount);
        }

        @Override
        public long insertToNetwork(AEKey key, long amount) {
            return delegate.insertToNetwork(key, amount);
        }

        @Override
        public void spawnToWorld(AEKey key, long amount) {
            delegate.spawnToWorld(key, amount);
        }
    }
}
