package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEKey;

class MatrixCraftingClusterTest {
    @Test
    void aggregatesCraftingProfileFromCraftCores() {
        var cluster = cluster(List.of(
                new FakeCraftCore(MatrixCraftingUnit.quantumCore(), MatrixCraftingUnit.t2Threader()),
                new FakeCraftCore(MatrixCraftingUnit.t2Multiplier(), MatrixCraftingUnit.t2Cooler(2))));

        var profile = cluster.craftingProfile();

        assertEquals(MatrixCoreMode.QUANTUM, profile.mode());
        assertEquals(4.0D, profile.threadPower(), 0.0001D);
        assertEquals(2.0D, profile.multiPower(), 0.0001D);
        assertEquals(1.5D, profile.coolPower(), 0.0001D);
    }

    @Test
    void limiterTickAdvancesAndPersistsHeatState() {
        var host = new FakeHost();
        host.time = 42;
        var cluster = cluster(host, List.of(new FakeCraftCore(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.threadPower(160),
                MatrixCraftingUnit.multiplierPower(20))));

        var snapshot = cluster.tickLimiter();
        var tag = new CompoundTag();
        cluster.writeEngineTo(tag, null);

        var restored = cluster(List.of());
        restored.readEngineFrom(tag, null);

        assertEquals(0.76793856D, snapshot.heat(), 0.0001D);
        assertEquals(snapshot.heat(), restored.heat(), 0.0001D);
        assertEquals(42L, restored.lastLimiterTick());
    }

    private static MatrixCraftingCluster cluster(List<FakeCraftCore> cores) {
        return cluster(new FakeHost(), cores);
    }

    private static MatrixCraftingCluster cluster(FakeHost host, List<FakeCraftCore> cores) {
        return new MatrixCraftingCluster(
                () -> true,
                List.of(),
                cores,
                host,
                (details, oneCopyInputs) -> null,
                new CraftingCoreRegistry());
    }

    private static final class FakeCraftCore implements MatrixCraftCore {
        private final List<MatrixCraftingUnit> units;

        FakeCraftCore(MatrixCraftingUnit... units) {
            this.units = List.of(units);
        }

        @Override
        public List<MatrixCraftingUnit> craftingUnits() {
            return units;
        }
    }

    private static final class FakeHost implements CraftingCoreHost {
        long time;

        @Override
        public long getGameTime() {
            return time;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public long insertToNetwork(AEKey key, long amount) {
            return amount;
        }

        @Override
        public void spawnToWorld(AEKey key, long amount) {
        }
    }
}
