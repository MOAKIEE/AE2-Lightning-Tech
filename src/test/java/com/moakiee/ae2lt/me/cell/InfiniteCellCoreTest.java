package com.moakiee.ae2lt.me.cell;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

class InfiniteCellCoreTest {

    // ═══════════════════════════════════════════════════════════════════
    //  Test stubs — lightweight AEKey / AEKeyType without MC bootstrap
    // ═══════════════════════════════════════════════════════════════════

    static final class StubKeyType extends AEKeyType {
        static final StubKeyType ITEMS = new StubKeyType("items", 8);
        static final StubKeyType FLUIDS = new StubKeyType("fluids", 1000);
        static final StubKeyType GAS = new StubKeyType("gas", 512);
        static final StubKeyType ESSENCE = new StubKeyType("essence", 64);
        static final StubKeyType ENERGY = new StubKeyType("energy", 2048);

        private final int apb;

        StubKeyType(String name, int apb) {
            super(ResourceLocation.fromNamespaceAndPath("test", name),
                    StubKey.class,
                    Component.literal(name));
            this.apb = apb;
        }

        @Override public MapCodec<? extends AEKey> codec() {
            return RecordCodecBuilder.mapCodec(b -> b.group(
                    Codec.INT.fieldOf("id").forGetter(k -> ((StubKey) k).id)
            ).apply(b, id -> new StubKey(id, this)));
        }
        @Override public int getAmountPerByte() { return apb; }
        @Override public int getAmountPerOperation() { return 1; }
        @Override public int getAmountPerUnit() { return 1; }
        @Override public @Nullable AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }

    static final class StubKey extends AEKey {
        final int id;
        final StubKeyType type;

        StubKey(int id, StubKeyType type) {
            this.id = id;
            this.type = type;
        }

        @Override public AEKeyType getType() { return type; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(HolderLookup.Provider r) {
            var tag = new CompoundTag();
            tag.putInt("id", id);
            return tag;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", "k" + id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf buf) {}
        @Override protected Component computeDisplayName() { return Component.literal("k" + id); }
        @Override public void addDrops(long amt, List<ItemStack> d, Level l, BlockPos p) {}
        @Override public boolean hasComponents() { return false; }

        static CompoundTag stubToTag(AEKey key, HolderLookup.Provider ignored) {
            var sk = (StubKey) key;
            var tag = new CompoundTag();
            tag.putString("#t", sk.type.getId().toString());
            tag.putInt("id", sk.id);
            return tag;
        }

        @Override public int hashCode() { return id * 1327 + System.identityHashCode(type); }
        @Override public boolean equals(Object o) {
            return o instanceof StubKey k && k.id == id && k.type == type;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private static final BigInteger TWO63 = BigInteger.ONE.shiftLeft(63);
    private static final long MAX = Long.MAX_VALUE;
    private static final HolderLookup.Provider EMPTY_REGISTRIES = RegistryAccess.EMPTY;

    static BigInteger to126(long hi, long lo) {
        return BigInteger.valueOf(hi).multiply(TWO63).add(BigInteger.valueOf(lo));
    }

    static StubKey itemKey(int id) { return new StubKey(id, StubKeyType.ITEMS); }
    static StubKey fluidKey(int id) { return new StubKey(id, StubKeyType.FLUIDS); }

    private IndexedStorage newStorage() {
        return new IndexedStorage();
    }

    private IndexedStorage newUnlimitedStorage() {
        return newStorage();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Part 1 — DualLong126 correctness
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class DualLong126Tests {

        @Test
        void addSubInlineBoundaries() {
            long[][] cases = {
                // {lo, amount, expectedLo, expectCarry(1/0)}
                {0, 0, 0, 0},
                {0, 1, 1, 0},
                {MAX - 1, 1, MAX, 0},
                {MAX, 1, 0, 1},                             // exact carry boundary
                {MAX, MAX, MAX - 1, 1},                     // max + max
                {MAX / 2, MAX / 2 + 1, MAX, 0},             // no carry, exact MAX
                {MAX / 2 + 1, MAX / 2 + 1, 0, 1},           // exactly 2^63 → carry, lo=0
                {MAX / 2 + 1, MAX / 2 + 2, 1, 1},           // just past carry
                {1, MAX, 0, 1},
            };
            for (long[] c : cases) {
                long lo = c[0], amt = c[1], expLo = c[2]; boolean expCarry = c[3] == 1;
                long sum = lo + amt;
                long gotLo = sum & MAX;
                boolean gotCarry = sum < 0;
                assertEquals(expLo, gotLo, "add lo=" + lo + " amt=" + amt);
                assertEquals(expCarry, gotCarry, "add carry lo=" + lo + " amt=" + amt);

                // sub round-trip: (gotLo) - amt should give back lo (with matching borrow)
                long diff = gotLo - amt;
                long backLo = diff & MAX;
                boolean gotBorrow = diff < 0;
                if (!expCarry) {
                    assertEquals(lo, backLo, "sub round-trip lo=" + lo + " amt=" + amt);
                    assertFalse(gotBorrow);
                } else {
                    assertEquals(lo, backLo, "sub round-trip (carry) lo=" + lo + " amt=" + amt);
                    assertTrue(gotBorrow);
                }
            }
        }

        @Test
        void addSubRandom10k() {
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 10_000; i++) {
                long lo = rng.nextLong() & MAX;       // [0, Long.MAX_VALUE]
                long amount = rng.nextLong() & MAX;

                BigInteger biLo = BigInteger.valueOf(lo);
                BigInteger biAmt = BigInteger.valueOf(amount);
                BigInteger biSum = biLo.add(biAmt);
                boolean expectedCarry = biSum.compareTo(TWO63) >= 0;
                long expectedLo = biSum.mod(TWO63).longValue();

                long sum = lo + amount;
                assertEquals(expectedLo, sum & MAX, "add random #" + i);
                assertEquals(expectedCarry, sum < 0, "add carry random #" + i);

                // sub: (expectedLo - amount) should recover lo with borrow == carry
                long diff = expectedLo - amount;
                assertEquals(lo, diff & MAX, "sub round-trip random #" + i);
                assertEquals(expectedCarry, diff < 0, "sub borrow random #" + i);
            }
        }

        @Test
        void geqAndCap() {
            assertTrue(DualLong126.geq(1, 0, MAX));
            assertTrue(DualLong126.geq(0, 100, 100));
            assertTrue(DualLong126.geq(0, 100, 50));
            assertFalse(DualLong126.geq(0, 49, 50));
            assertFalse(DualLong126.geq(0, 0, 1));
            assertTrue(DualLong126.geq(0, 0, 0));

            assertEquals(MAX, DualLong126.cap(1, 0));
            assertEquals(MAX, DualLong126.cap(1, 42));
            assertEquals(0, DualLong126.cap(0, 0));
            assertEquals(42, DualLong126.cap(0, 42));
            assertEquals(MAX, DualLong126.cap(0, MAX));
        }

        @Test
        void mod126_crossValidation10k() {
            var rng = ThreadLocalRandom.current();
            int[] divisors = {2, 3, 7, 8, 10, 64, 100, 128, 1000, 8192, 65536, Integer.MAX_VALUE};
            for (int d : divisors) {
                for (int i = 0; i < 1_000; i++) {
                    long hi = rng.nextLong() & MAX;
                    long lo = rng.nextLong() & MAX;
                    BigInteger val = to126(hi, lo);
                    long expected = val.mod(BigInteger.valueOf(d)).longValue();
                    long actual = DualLong126.mod126(hi, lo, d);
                    assertEquals(expected, actual,
                            "mod126 hi=" + hi + " lo=" + lo + " d=" + d);
                }
            }
            // d=1 special case
            assertEquals(0, DualLong126.mod126(12345, 67890, 1));
        }

        @Test
        void ceilDiv126_crossValidation10k() {
            var rng = ThreadLocalRandom.current();
            int[] divisors = {2, 3, 7, 8, 10, 64, 100, 128, 1000, 8192};
            long[] out = new long[2];

            for (int d : divisors) {
                for (int i = 0; i < 1_000; i++) {
                    long hi = rng.nextLong() & MAX;
                    long lo = rng.nextLong() & MAX;
                    BigInteger val = to126(hi, lo);
                    BigInteger[] qr = val.divideAndRemainder(BigInteger.valueOf(d));
                    BigInteger expected = qr[1].signum() > 0 ? qr[0].add(BigInteger.ONE) : qr[0];
                    long expHi = expected.shiftRight(63).longValue();
                    long expLo = expected.and(BigInteger.valueOf(MAX)).longValue();

                    DualLong126.ceilDiv126(hi, lo, d, out);
                    assertEquals(expHi, out[0],
                            "ceilDiv126 hi part: hi=" + hi + " lo=" + lo + " d=" + d);
                    assertEquals(expLo, out[1],
                            "ceilDiv126 lo part: hi=" + hi + " lo=" + lo + " d=" + d);
                }
            }

            // d=1 identity
            DualLong126.ceilDiv126(42, 99, 1, out);
            assertEquals(42, out[0]);
            assertEquals(99, out[1]);

            // hi=0 fast path
            DualLong126.ceilDiv126(0, 17, 8, out);
            assertEquals(0, out[0]);
            assertEquals(3, out[1]); // ceil(17/8) = 3
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Part 2 — Storage engine correctness
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class StorageEngineTests {

        @Test
        void insertExtractSingleKey() {
            var s = newUnlimitedStorage();
            var k = itemKey(1);
            assertEquals(100, s.insert(k, 100, Actionable.MODULATE));
            assertEquals(100, s.getAmount(k));
            assertEquals(50, s.extract(k, 50, Actionable.MODULATE));
            assertEquals(50, s.getAmount(k));
            assertEquals(50, s.extract(k, 999, Actionable.MODULATE));
            assertEquals(0, s.getAmount(k));
            assertFalse(s.containsKey(k));
        }

        @Test
        void simulateDoesNotModify() {
            var s = newUnlimitedStorage();
            var k = itemKey(1);
            s.insert(k, 100, Actionable.MODULATE);
            assertEquals(50, s.insert(k, 50, Actionable.SIMULATE));
            assertEquals(100, s.getAmount(k));
            assertEquals(30, s.extract(k, 30, Actionable.SIMULATE));
            assertEquals(100, s.getAmount(k));
        }

        @Test
        void insertMultipleKeysAndTypes() {
            var s = newUnlimitedStorage();
            for (int i = 0; i < 50; i++) {
                s.insert(itemKey(i), (i + 1) * 100L, Actionable.MODULATE);
            }
            for (int i = 0; i < 10; i++) {
                s.insert(fluidKey(i), (i + 1) * 5000L, Actionable.MODULATE);
            }
            assertEquals(60, s.getTotalTypes());

            var kc = new KeyCounter();
            s.getAvailableStacks(kc);
            assertEquals(60, kc.size());

            for (int i = 0; i < 50; i++) {
                assertEquals((i + 1) * 100L, s.getAmount(itemKey(i)),
                        "item key " + i);
            }
            for (int i = 0; i < 10; i++) {
                assertEquals((i + 1) * 5000L, s.getAmount(fluidKey(i)),
                        "fluid key " + i);
            }
        }

        @Test
        void insertCausesCarry() {
            var s = newUnlimitedStorage();
            var k = itemKey(0);
            s.insert(k, MAX, Actionable.MODULATE);
            s.insert(k, MAX, Actionable.MODULATE);
            // total = 2 * MAX = 2 * (2^63-1) which in 126-bit = hi:1, lo:(MAX-1)
            // getAmount caps to MAX
            assertEquals(MAX, s.getAmount(k));
            assertTrue(s.containsKey(k));
        }

        @Test
        void extractWithBorrow() {
            var s = newUnlimitedStorage();
            var k = itemKey(0);
            // insert MAX twice → hi=1, lo=MAX-1
            s.insert(k, MAX, Actionable.MODULATE);
            s.insert(k, MAX, Actionable.MODULATE);
            // extract MAX → should succeed (total > MAX)
            assertEquals(MAX, s.extract(k, MAX, Actionable.MODULATE));
            // remaining = 2*MAX - MAX = MAX
            assertEquals(MAX, s.getAmount(k));
        }

        @Test
        void extractAllRemovesKey() {
            var s = newUnlimitedStorage();
            var k = itemKey(0);
            s.insert(k, 42, Actionable.MODULATE);
            assertEquals(1, s.getTotalTypes());
            assertEquals(42, s.extract(k, 42, Actionable.MODULATE));
            assertEquals(0, s.getTotalTypes());
            assertFalse(s.containsKey(k));
        }

        @Test
        void extractMoreThanAvailable() {
            var s = newUnlimitedStorage();
            var k = itemKey(0);
            s.insert(k, 50, Actionable.MODULATE);
            long taken = s.extract(k, 999, Actionable.MODULATE);
            assertEquals(50, taken);
            assertEquals(0, s.getTotalTypes());
        }

        @Test
        void extractFromEmptyReturnsZero() {
            var s = newUnlimitedStorage();
            assertEquals(0, s.extract(itemKey(99), 100, Actionable.MODULATE));
        }

        @Test
        void partitionExpandAndShrink() {
            var s = newUnlimitedStorage();
            StubKey[] keys = new StubKey[200];
            for (int i = 0; i < 200; i++) keys[i] = itemKey(i);

            for (int i = 0; i < 200; i++) {
                s.insert(keys[i], 10, Actionable.MODULATE);
            }
            assertEquals(200, s.getTotalTypes());

            // verify all keys accessible via direct storage lookup
            for (int i = 0; i < 200; i++) {
                assertTrue(s.containsKey(keys[i]), "containsKey " + i);
                assertEquals(10L, s.getAmount(keys[i]), "amount key " + i);
            }

            // extract all to trigger shrink
            for (int i = 0; i < 200; i++) {
                assertEquals(10L, s.extract(keys[i], 10, Actionable.MODULATE),
                        "extract key " + i);
            }
            assertEquals(0, s.getTotalTypes());
        }

        @Test
        void massRandomInsertExtract10k() {
            var s = newUnlimitedStorage();
            var rng = ThreadLocalRandom.current();
            int keyCount = 500;
            StubKey[] keys = new StubKey[keyCount];
            for (int i = 0; i < keyCount; i++) keys[i] = itemKey(i);
            var reference = new HashMap<Integer, Long>();

            for (int op = 0; op < 10_000; op++) {
                int kid = rng.nextInt(keyCount);
                boolean doInsert = rng.nextBoolean() || !reference.containsKey(kid);
                if (doInsert) {
                    long amount = rng.nextLong(1, 1_000_000);
                    s.insert(keys[kid], amount, Actionable.MODULATE);
                    reference.merge(kid, amount, Long::sum);
                } else {
                    long stored = reference.getOrDefault(kid, 0L);
                    if (stored > 0) {
                        long toExtract = rng.nextLong(1, stored + 1);
                        long taken = s.extract(keys[kid], toExtract, Actionable.MODULATE);
                        assertEquals(toExtract, taken, "extract op #" + op);
                        long newAmt = stored - toExtract;
                        if (newAmt == 0) reference.remove(kid);
                        else reference.put(kid, newAmt);
                    }
                }
            }

            // verify final state via direct storage lookup
            int expectedTypes = (int) reference.values().stream().filter(v -> v > 0).count();
            assertEquals(expectedTypes, s.getTotalTypes(), "final type count");

            for (var entry : reference.entrySet()) {
                long expected = entry.getValue();
                long actual = s.getAmount(keys[entry.getKey()]);
                assertEquals(expected, actual, "final amount key=" + entry.getKey());
            }
        }

        @Test
        void byteTrackerConsistency() {
            int bytesPerType = 8;
            int apb = 8; // StubKeyType.ITEMS
            var s = newStorage();
            var rng = ThreadLocalRandom.current();
            var reference = new HashMap<Integer, Long>();

            for (int op = 0; op < 5_000; op++) {
                int kid = rng.nextInt(100);
                var key = itemKey(kid);
                boolean doInsert = rng.nextBoolean() || !reference.containsKey(kid);
                if (doInsert) {
                    long amount = rng.nextLong(1, 100_000);
                    s.insert(key, amount, Actionable.MODULATE);
                    reference.merge(kid, amount, Long::sum);
                } else {
                    long stored = reference.getOrDefault(kid, 0L);
                    if (stored > 0) {
                        long toExtract = rng.nextLong(1, Math.min(stored + 1, 100_000));
                        s.extract(key, toExtract, Actionable.MODULATE);
                        long newAmt = stored - toExtract;
                        if (newAmt == 0) reference.remove(kid);
                        else reference.put(kid, newAmt);
                    }
                }
            }

            long totalAmount = reference.values().stream().mapToLong(Long::longValue).sum();
            long expectedBytes = (long) reference.size() * bytesPerType
                    + ceilDiv(totalAmount, apb);

            var tracker = new ByteTracker(() -> reference.size());
            tracker.configure(bytesPerType, Integer.MAX_VALUE, MAX, 0);
            tracker.rebuild(s.getTypeAmountLo(), s.getTypeAmountHi(),
                    s.getTypeCounts(), s.getTotalTypes());

            assertEquals(expectedBytes, tracker.getUsedBytes(),
                    "byte tracker usedBytes after rebuild");
        }

        private long ceilDiv(long a, int d) {
            return (a + d - 1) / d;
        }

        /** Mimics InfiniteCellInventory insert logic for unit-testing capacity. */
        private long insertWithLimit(IndexedStorage s, ByteTracker bt, AEKey key, long amount) {
            boolean isNewKey = !s.containsKey(key);
            long max = bt.computeMaxInsertable(key.getType(), isNewKey);
            if (max <= 0) return 0;
            long toInsert = Math.min(amount, max);
            s.insert(key, toInsert, Actionable.MODULATE);
            bt.onInsert(key.getType(), toInsert, isNewKey);
            return toInsert;
        }

        @Test
        void capacityLimitsInsertion() {
            var s = newStorage();
            var bt = new ByteTracker(s::getTotalTypes);
            bt.configure(8, 10, 100, 0);

            var k = itemKey(0);
            // inserting: needs 8 (type overhead) + ceil(amt/8) <= 100
            // max amount bytes = 92, max amount = 92 * 8 = 736
            long inserted = insertWithLimit(s, bt, k, 1000);
            assertEquals(736, inserted, "capacity-limited insert");
            assertEquals(0, insertWithLimit(s, bt, k, 1));
        }

        @Test
        void maxTypesLimitsNewKeys() {
            var s = newStorage();
            var bt = new ByteTracker(s::getTotalTypes);
            bt.configure(8, 3, MAX, 0);

            insertWithLimit(s, bt, itemKey(0), 1);
            insertWithLimit(s, bt, itemKey(1), 1);
            insertWithLimit(s, bt, itemKey(2), 1);
            assertEquals(0, insertWithLimit(s, bt, itemKey(3), 1));
            assertTrue(insertWithLimit(s, bt, itemKey(0), 1) > 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Part 3 — Performance benchmarks
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class PerformanceBenchmarks {

        private static final int KEY_COUNT = 10_000;
        private static final int OPS = 1_000_000;

        @Test
        void benchmarkInsert1M() {
            var s = newUnlimitedStorage();
            StubKey[] keys = new StubKey[KEY_COUNT];
            for (int i = 0; i < KEY_COUNT; i++) keys[i] = itemKey(i);

            var rng = ThreadLocalRandom.current();
            long[] amounts = new long[OPS];
            int[] keyIndices = new int[OPS];
            for (int i = 0; i < OPS; i++) {
                amounts[i] = rng.nextLong(1, 10_000);
                keyIndices[i] = rng.nextInt(KEY_COUNT);
            }

            // warm up
            for (int i = 0; i < 50_000; i++) {
                s.insert(keys[keyIndices[i]], amounts[i], Actionable.MODULATE);
            }
            s = newUnlimitedStorage();

            long t0 = System.nanoTime();
            for (int i = 0; i < OPS; i++) {
                s.insert(keys[keyIndices[i]], amounts[i], Actionable.MODULATE);
            }
            long elapsed = System.nanoTime() - t0;

            System.out.printf("[INSERT 1M] %,d ops in %,d ms  —  %.1f ns/op  —  %.1f M ops/s%n",
                    OPS, elapsed / 1_000_000,
                    (double) elapsed / OPS,
                    OPS / (elapsed / 1e9) / 1e6);
        }

        @Test
        void benchmarkExtract1M() {
            var s = newUnlimitedStorage();
            StubKey[] keys = new StubKey[KEY_COUNT];
            for (int i = 0; i < KEY_COUNT; i++) {
                keys[i] = itemKey(i);
                s.insert(keys[i], Long.MAX_VALUE / 2, Actionable.MODULATE);
            }

            var rng = ThreadLocalRandom.current();
            long[] amounts = new long[OPS];
            int[] keyIndices = new int[OPS];
            for (int i = 0; i < OPS; i++) {
                amounts[i] = rng.nextLong(1, 10_000);
                keyIndices[i] = rng.nextInt(KEY_COUNT);
            }

            // warm up
            for (int i = 0; i < 50_000; i++) {
                s.extract(keys[keyIndices[i]], amounts[i], Actionable.MODULATE);
            }
            // refill
            for (int i = 0; i < KEY_COUNT; i++) {
                s.insert(keys[i], Long.MAX_VALUE / 4, Actionable.MODULATE);
            }

            long t0 = System.nanoTime();
            for (int i = 0; i < OPS; i++) {
                s.extract(keys[keyIndices[i]], amounts[i], Actionable.MODULATE);
            }
            long elapsed = System.nanoTime() - t0;

            System.out.printf("[EXTRACT 1M] %,d ops in %,d ms  —  %.1f ns/op  —  %.1f M ops/s%n",
                    OPS, elapsed / 1_000_000,
                    (double) elapsed / OPS,
                    OPS / (elapsed / 1e9) / 1e6);
        }

        @Test
        void benchmarkMixed1M() {
            var s = newUnlimitedStorage();
            StubKey[] keys = new StubKey[KEY_COUNT];
            for (int i = 0; i < KEY_COUNT; i++) {
                keys[i] = itemKey(i);
                s.insert(keys[i], Long.MAX_VALUE / 4, Actionable.MODULATE);
            }

            var rng = ThreadLocalRandom.current();
            long[] amounts = new long[OPS];
            int[] keyIndices = new int[OPS];
            boolean[] isInsert = new boolean[OPS];
            for (int i = 0; i < OPS; i++) {
                amounts[i] = rng.nextLong(1, 10_000);
                keyIndices[i] = rng.nextInt(KEY_COUNT);
                isInsert[i] = rng.nextBoolean();
            }

            // warm up
            for (int i = 0; i < 50_000; i++) {
                if (isInsert[i]) s.insert(keys[keyIndices[i]], amounts[i], Actionable.MODULATE);
                else s.extract(keys[keyIndices[i]], amounts[i], Actionable.MODULATE);
            }
            // reset
            s = newUnlimitedStorage();
            for (int i = 0; i < KEY_COUNT; i++) {
                s.insert(keys[i], Long.MAX_VALUE / 4, Actionable.MODULATE);
            }

            long t0 = System.nanoTime();
            for (int i = 0; i < OPS; i++) {
                if (isInsert[i]) s.insert(keys[keyIndices[i]], amounts[i], Actionable.MODULATE);
                else s.extract(keys[keyIndices[i]], amounts[i], Actionable.MODULATE);
            }
            long elapsed = System.nanoTime() - t0;

            System.out.printf("[MIXED 1M] %,d ops in %,d ms  —  %.1f ns/op  —  %.1f M ops/s%n",
                    OPS, elapsed / 1_000_000,
                    (double) elapsed / OPS,
                    OPS / (elapsed / 1e9) / 1e6);
        }

        @Test
        void profileLongRunning() {
            StubKeyType[] types = {
                    StubKeyType.ITEMS, StubKeyType.FLUIDS, StubKeyType.GAS,
                    StubKeyType.ESSENCE, StubKeyType.ENERGY
            };
            int keysPerType = 50_000;
            int totalKeys = keysPerType * types.length;
            int persistInterval = 1024;

            StubKey[][] keys = new StubKey[types.length][keysPerType];
            for (int t = 0; t < types.length; t++)
                for (int i = 0; i < keysPerType; i++)
                    keys[t][i] = new StubKey(i, types[t]);

            var warmup = newUnlimitedStorage();
            for (int t = 0; t < types.length; t++)
                for (int i = 0; i < keysPerType; i++)
                    warmup.insert(keys[t][i], 100, Actionable.MODULATE);
            warmup.persist(null, StubKey::stubToTag, null);
            warmup = null;

            long targetNs = 15_000_000_000L;
            var rng = new SplittableRandom(42);
            var s = newUnlimitedStorage();
            CompoundTag lastPersisted = null;
            int opCounter = 0;
            long totalOps = 0;

            long t0 = System.nanoTime();
            while (System.nanoTime() - t0 < targetNs) {
                for (int t = 0; t < types.length; t++) {
                    for (int i = 0; i < keysPerType; i++) {
                        long amount = rng.nextLong(1, 10_000);
                        if (rng.nextBoolean() && s.containsKey(keys[t][i])) {
                            s.extract(keys[t][i], amount, Actionable.MODULATE);
                        } else {
                            s.insert(keys[t][i], amount, Actionable.MODULATE);
                        }
                        totalOps++;
                        if (++opCounter % persistInterval == 0) {
                            lastPersisted = s.persist(lastPersisted, StubKey::stubToTag, null);
                        }
                    }
                }
            }
            long elapsed = System.nanoTime() - t0;

            System.out.printf("[PROFILE] %,d ops in %,d ms — %.1f ns/op — %.1f M ops/s%n",
                    totalOps, elapsed / 1_000_000,
                    (double) elapsed / totalOps,
                    totalOps / (elapsed / 1e9) / 1e6);
            System.out.printf("  %,d types, persist every %,d ops%n", totalKeys, persistInterval);
        }

        @Test
        void benchmarkStructuredInsert10M() {
            StubKeyType[] types = {
                    StubKeyType.ITEMS, StubKeyType.FLUIDS, StubKeyType.GAS,
                    StubKeyType.ESSENCE, StubKeyType.ENERGY
            };
            int keysPerType = 200_000;
            int opsPerKey = 10;
            int totalKeys = keysPerType * types.length;       // 1,000,000
            int totalOps = totalKeys * opsPerKey;              // 10,000,000
            int persistInterval = 1024;

            // pre-create all keys
            StubKey[][] keys = new StubKey[types.length][keysPerType];
            for (int t = 0; t < types.length; t++) {
                for (int i = 0; i < keysPerType; i++) {
                    keys[t][i] = new StubKey(i, types[t]);
                }
            }

            // warm up JIT with a small run
            var warmup = newUnlimitedStorage();
            for (int t = 0; t < types.length; t++) {
                for (int i = 0; i < 50_000; i++) {
                    warmup.insert(keys[t][i], 100, Actionable.MODULATE);
                }
            }
            warmup.persist(null, StubKey::stubToTag, null);
            warmup = null;

            // actual run
            var s = newUnlimitedStorage();
            long[][] expected = new long[types.length][keysPerType];
            long[] perTypeNs = new long[types.length];
            var rng = new SplittableRandom(42);
            int opCounter = 0;
            long persistNs = 0;
            int persistCount = 0;

            CompoundTag lastPersisted = null;

            long totalT0 = System.nanoTime();
            for (int op = 0; op < opsPerKey; op++) {
                for (int t = 0; t < types.length; t++) {
                    long tStart = System.nanoTime();
                    for (int i = 0; i < keysPerType; i++) {
                        long amount = rng.nextLong(1, 10_000);
                        s.insert(keys[t][i], amount, Actionable.MODULATE);
                        expected[t][i] += amount;

                        if (++opCounter % persistInterval == 0) {
                            long pStart = System.nanoTime();
                            lastPersisted = s.persist(lastPersisted, StubKey::stubToTag, null);
                            persistNs += System.nanoTime() - pStart;
                            persistCount++;
                        }
                    }
                    perTypeNs[t] += System.nanoTime() - tStart;
                }
            }
            long insertElapsed = System.nanoTime() - totalT0;
            long insertOnly = insertElapsed - persistNs;

            System.out.printf("[STRUCTURED INSERT 10M + PERSIST] %,d ops in %,d ms%n",
                    totalOps, insertElapsed / 1_000_000);
            System.out.printf("  Insert only:  %,d ms  —  %.1f ns/op  —  %.1f M ops/s%n",
                    insertOnly / 1_000_000,
                    (double) insertOnly / totalOps,
                    totalOps / (insertOnly / 1e9) / 1e6);
            System.out.printf("  Persist total: %,d ms  (%,d calls, avg %.2f ms/call)%n",
                    persistNs / 1_000_000,
                    persistCount,
                    persistNs / 1e6 / persistCount);
            System.out.printf("  Combined:      %.1f ns/op  —  %.1f M ops/s%n",
                    (double) insertElapsed / totalOps,
                    totalOps / (insertElapsed / 1e9) / 1e6);
            System.out.printf("  Layout: %d keyTypes × %,d keys × %d ops/key, persist every %,d ops%n",
                    types.length, keysPerType, opsPerKey, persistInterval);

            int opsPerType = keysPerType * opsPerKey;
            for (int t = 0; t < types.length; t++) {
                System.out.printf("  %-10s (apb=%4d): %,5d ms  %5.1f ns/op  %5.1f%%%n",
                        types[t].getId().getPath(),
                        types[t].getAmountPerByte(),
                        perTypeNs[t] / 1_000_000,
                        (double) perTypeNs[t] / opsPerType,
                        perTypeNs[t] * 100.0 / insertElapsed);
            }

            // verify final state
            assertEquals(totalKeys, s.getTotalTypes(), "total unique keys");

            long verifyT0 = System.nanoTime();
            for (int t = 0; t < types.length; t++) {
                String typeName = types[t].getId().getPath();
                for (int i = 0; i < keysPerType; i++) {
                    long actual = s.getAmount(keys[t][i]);
                    assertEquals(expected[t][i], actual,
                            typeName + "[" + i + "]");
                }
            }
            long verifyElapsed = System.nanoTime() - verifyT0;

            System.out.printf("  Verify: %,d keys in %,d ms%n", totalKeys, verifyElapsed / 1_000_000);
        }
    }
}
