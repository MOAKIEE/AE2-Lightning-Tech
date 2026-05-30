package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

class CraftingCoreTest {
    @Test
    void pushBatchAssemblesOnceAndDeliversAfterDelay() {
        var host = new FakeHost(100, 10, 1_000);
        var registry = new CraftingCoreRegistry();
        var assembler = new FakeAssembler(key("diamond"), 2, stack(key("bucket"), 1));
        var core = new CraftingCore(host, new CoreParams(2, 20.0D), assembler, registry);
        var input = key("stick");

        int leftover = core.pushBatch(new FakePattern(), scaled(input, 5), 5);

        assertEquals(0, leftover);
        assertEquals(1, assembler.calls);
        assertEquals(5, core.threadsInFlight());

        host.time = 101;
        registry.tickAll();
        assertEquals(0, host.network.getLong(key("diamond")));

        host.time = 102;
        registry.tickAll();
        assertEquals(10, host.network.getLong(key("diamond")));
        assertEquals(5, host.network.getLong(key("bucket")));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void capacityLimitsCopiesAndReturnsLeftover() {
        var host = new FakeHost(0, 3, 1_000);
        var core = new CraftingCore(
                host,
                new CoreParams(1, 20.0D),
                new FakeAssembler(key("diamond"), 1),
                new CraftingCoreRegistry());

        int leftover = core.pushBatch(new FakePattern(), scaled(key("stick"), 7), 7);

        assertEquals(4, leftover);
        assertEquals(3, core.threadsInFlight());
    }

    @Test
    void availableCapacitySweepsCompletedWorkBeforeTheRegistryTickRuns() {
        var host = new FakeHost(0, 2, 1_000);
        var core = new CraftingCore(
                host,
                new CoreParams(2, 20.0D),
                new FakeAssembler(key("diamond"), 1),
                new CraftingCoreRegistry());

        assertEquals(0, core.pushBatch(new FakePattern(), scaled(key("stick"), 2), 2));
        assertEquals(0, core.availableCapacity());

        host.time = 2;

        assertEquals(2, core.availableCapacity());
        assertEquals(2, host.network.getLong(key("diamond")));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void oneTickDelayLetsCapacityRefillEveryTick() {
        var host = new FakeHost(0, 2, 1_000);
        var core = new CraftingCore(
                host,
                new CoreParams(1, 20.0D),
                new FakeAssembler(key("diamond"), 1),
                new CraftingCoreRegistry());

        assertEquals(0, core.pushBatch(new FakePattern(), scaled(key("stick"), 2), 2));
        host.time = 1;

        assertEquals(2, core.availableCapacity());
        assertEquals(0, core.pushBatch(new FakePattern(), scaled(key("stick"), 2), 2));
        assertEquals(0, core.availableCapacity());
    }

    @Test
    void energyShortageScalesCopiesDownAndRefundsUnusedEnergy() {
        var host = new FakeHost(0, 10, 55.0D);
        var core = new CraftingCore(
                host,
                new CoreParams(1, 20.0D),
                new FakeAssembler(key("diamond"), 1),
                new CraftingCoreRegistry());

        int leftover = core.pushBatch(new FakePattern(), scaled(key("stick"), 5), 5);

        assertEquals(3, leftover);
        assertEquals(2, core.threadsInFlight());
        assertEquals(15.0D, host.energy, 0.0001D);
    }

    @Test
    void mixedKeysInOneSlotAreRejectedWithoutSideEffects() {
        var host = new FakeHost(0, 10, 1_000);
        var assembler = new FakeAssembler(key("diamond"), 1);
        var core = new CraftingCore(host, new CoreParams(1, 20.0D), assembler, new CraftingCoreRegistry());
        var slot = new KeyCounter();
        slot.add(key("stick"), 3);
        slot.add(key("cobble"), 3);

        int leftover = core.pushBatch(new FakePattern(), new KeyCounter[] {slot}, 3);

        assertEquals(3, leftover);
        assertEquals(0, assembler.calls);
        assertEquals(1_000.0D, host.energy, 0.0001D);
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void nonMolecularPatternsAreRejectedWithoutSideEffects() {
        var host = new FakeHost(0, 10, 1_000);
        var assembler = new FakeAssembler(key("diamond"), 1);
        var core = new CraftingCore(host, new CoreParams(1, 20.0D), assembler, new CraftingCoreRegistry());

        int leftover = core.pushBatch(new FakePlainPattern(), scaled(key("stick"), 3), 3);

        assertEquals(3, leftover);
        assertEquals(0, assembler.calls);
        assertEquals(1_000.0D, host.energy, 0.0001D);
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void partialNetworkInsertKeepsThreadBudgetUntilFullyDrained() {
        var host = new FakeHost(0, 2, 1_000);
        host.maxInsertPerCall = 3;
        var output = key("diamond");
        var core = new CraftingCore(
                host,
                new CoreParams(1, 20.0D),
                new FakeAssembler(output, 5),
                new CraftingCoreRegistry());

        assertEquals(0, core.pushBatch(new FakePattern(), scaled(key("stick"), 2), 2));

        host.time = 1;
        core.sweepTick();
        assertEquals(3, host.network.getLong(output));
        assertEquals(2, core.threadsInFlight());
        assertEquals(1, core.pushBatch(new FakePattern(), scaled(key("stick"), 1), 1));

        host.maxInsertPerCall = Long.MAX_VALUE;
        host.time = 2;
        core.sweepTick();
        assertEquals(10, host.network.getLong(output));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void disconnectedHostKeepsOutputsInWheelUntilReconnect() {
        var host = new FakeHost(0, 2, 1_000);
        host.connected = false;
        var output = key("diamond");
        var core = new CraftingCore(
                host,
                new CoreParams(1, 20.0D),
                new FakeAssembler(output, 1),
                new CraftingCoreRegistry());

        core.pushBatch(new FakePattern(), scaled(key("stick"), 2), 2);
        host.time = 1;
        core.sweepTick();

        assertEquals(0, host.network.getLong(output));
        assertEquals(2, core.threadsInFlight());

        host.connected = true;
        host.time = 2;
        core.sweepTick();

        assertEquals(2, host.network.getLong(output));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void removedHostHardFlushesToWorldAndDeregisters() {
        var host = new FakeHost(0, 2, 1_000);
        var output = key("diamond");
        var registry = new CraftingCoreRegistry();
        var core = new CraftingCore(
                host,
                new CoreParams(1, 20.0D),
                new FakeAssembler(output, 1),
                registry);

        core.pushBatch(new FakePattern(), scaled(key("stick"), 2), 2);
        host.removed = true;

        assertFalse(core.sweepTick());
        assertEquals(2, host.spawned.getLong(output));
        assertEquals(0, core.threadsInFlight());
    }

    private static KeyCounter[] scaled(AEKey key, long amount) {
        var counter = new KeyCounter();
        counter.add(key, amount);
        return new KeyCounter[] {counter};
    }

    private static TestKey key(String id) {
        return new TestKey(id);
    }

    private static CopyAssembler.Stack stack(AEKey key, long count) {
        return new CopyAssembler.Stack(key, count);
    }

    private static final class FakeHost implements CraftingCoreHost {
        long time;
        int maxThreads;
        double energy;
        boolean connected = true;
        boolean removed;
        long maxInsertPerCall = Long.MAX_VALUE;
        final Object2LongOpenHashMap<AEKey> network = new Object2LongOpenHashMap<>();
        final Object2LongOpenHashMap<AEKey> spawned = new Object2LongOpenHashMap<>();

        FakeHost(long time, int maxThreads, double energy) {
            this.time = time;
            this.maxThreads = maxThreads;
            this.energy = energy;
        }

        @Override
        public long getGameTime() {
            return time;
        }

        @Override
        public int maxThreads() {
            return maxThreads;
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public double extractEnergy(double amount) {
            double extracted = Math.min(amount, energy);
            energy -= extracted;
            return extracted;
        }

        @Override
        public void injectEnergy(double amount) {
            energy += amount;
        }

        @Override
        public long insertToNetwork(AEKey key, long amount) {
            long inserted = Math.min(amount, maxInsertPerCall);
            network.addTo(key, inserted);
            return inserted;
        }

        @Override
        public void spawnToWorld(AEKey key, long amount) {
            spawned.addTo(key, amount);
        }
    }

    private static final class FakeAssembler implements CopyAssembler {
        final AEKey output;
        final long outputCount;
        final List<Stack> remainders;
        int calls;

        FakeAssembler(AEKey output, long outputCount, Stack... remainders) {
            this.output = output;
            this.outputCount = outputCount;
            this.remainders = List.of(remainders);
        }

        @Override
        public AssembledCopy assembleOneCopy(IPatternDetails details, KeyCounter[] oneCopyInputs) {
            calls++;
            return new AssembledCopy(output, outputCount, remainders);
        }
    }

    private static final class FakePlainPattern implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }
    }

    private static final class FakePattern implements IMolecularAssemblerSupportedPattern {
        @Override
        public ItemStack assemble(CraftingInput input, Level level) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slotIndex, AEItemKey key, Level level) {
            return true;
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            return true;
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] inputHolder, CraftingGridAccessor accessor) {
        }

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            return NonNullList.create();
        }
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "key"), TestKey.class,
                    Component.literal("test key"));
        }

        @Override
        public MapCodec<? extends AEKey> codec() {
            return null;
        }

        @Override
        public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
            return null;
        }
    }
}
