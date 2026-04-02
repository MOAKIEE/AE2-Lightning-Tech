package com.moakiee.ae2lt.machine.wireless;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.RandomSource;

/**
 * Generates unique wireless frequencies. Frequencies are random longs
 * that never collide with previously generated values in this session.
 * <p>
 * Call {@link #markUsed(long)} when loading existing frequencies from NBT
 * to prevent collisions with persisted data.
 */
public final class FreqGenerator {

    public static final FreqGenerator INSTANCE = new FreqGenerator();

    private final LongOpenHashSet used = new LongOpenHashSet();
    private final RandomSource random = RandomSource.create();

    private FreqGenerator() {
    }

    public synchronized long genFreq() {
        long f = random.nextLong();
        while (f == 0 || used.contains(f)) {
            f = random.nextLong();
        }
        used.add(f);
        return f;
    }

    public synchronized void markUsed(long freq) {
        if (freq != 0) {
            used.add(freq);
        }
    }
}
