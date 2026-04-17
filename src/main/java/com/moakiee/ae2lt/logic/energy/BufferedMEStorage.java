package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

/**
 * Two-tier FE cache in front of an ME storage delegate.
 *
 * <p>Operates in one of three modes:
 * <ul>
 * <li><b>Cell-backed</b> (preferred for Overloaded Power Supply when a Flux
 *     Cell is installed): cache is physically stored in the cell. Reads
 *     drain the cell first; when the cell can't satisfy a request, the
 *     cache refills itself in one ME pull sized to
 *     {@code recentConsumption + currentDemand} (capped by the cell's free
 *     capacity). FE never flows from the cell back to ME during normal
 *     operation — the cell IS the storage, and its NBT persists across
 *     unload/restart.</li>
 * <li><b>Legacy RAM</b> (used by Overloaded Pattern Provider's batch
 *     dispatch): uses the per-instance {@code feBuffer} field as a transient
 *     buffer, bounded and flushed at the end of each batch.</li>
 * <li><b>Pass-through</b> (Power Supply with no cell installed,
 *     {@code cellSupplier} returns null AND {@code feBufferCapacity == 0}):
 *     every call forwards directly to the ME delegate with no caching. This
 *     is the "plain AppFlux forward" behavior the main-branch Pattern
 *     Provider uses when no induction cache is present.</li>
 * </ul>
 */
public class BufferedMEStorage implements MEStorage {

    private static final int HISTORY_SIZE = 20;

    private final MEStorage delegate;
    @Nullable
    private final Supplier<MEStorage> cellSupplier;
    private final long[] consumptionHistory = new long[HISTORY_SIZE];

    private long feBuffer; // RAM cache, only used when cellSupplier yields null
    private long feBufferCapacity;
    private int historyPointer;
    private int costMultiplier = 1;

    public BufferedMEStorage(MEStorage delegate) {
        this(delegate, null);
    }

    /**
     * @param delegate     ME-network storage to refill from / flush to.
     * @param cellSupplier live getter for the Flux Cell's {@link MEStorage}
     *                     (the cell currently installed in the host). Return
     *                     {@code null} when no valid cell is present; the
     *                     class will fall back to an in-memory buffer.
     */
    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier) {
        this.delegate = delegate;
        this.cellSupplier = cellSupplier;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!isFeKey(what) || amount <= 0) {
            return delegate.extract(what, amount, mode, source);
        }

        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null) {
            return 0L;
        }

        long needed = amount * costMultiplier;
        MEStorage cell = resolveCell();

        if (cell != null) {
            return extractFromCell(feKey, cell, needed, mode, source) / costMultiplier;
        }

        if (feBufferCapacity <= 0L) {
            long extracted = delegate.extract(feKey, needed, mode, source);
            if (mode == Actionable.MODULATE) {
                recordConsumption(extracted);
            }
            return extracted / costMultiplier;
        }

        return extractFromRam(feKey, needed, mode, source) / costMultiplier;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!isFeKey(what) || amount <= 0) {
            return delegate.insert(what, amount, mode, source);
        }

        long credit = amount * costMultiplier;
        MEStorage cell = resolveCell();
        long accepted;

        if (cell != null) {
            long intoCell = cell.insert(what, credit, mode, source);
            long overflow = credit - intoCell;
            long intoMe = overflow > 0L ? delegate.insert(what, overflow, mode, source) : 0L;
            accepted = intoCell + intoMe;
        } else if (feBufferCapacity <= 0L) {
            accepted = delegate.insert(what, credit, mode, source);
        } else {
            long spaceLeft = Math.max(0L, feBufferCapacity - feBuffer);
            long fitted = Math.min(credit, spaceLeft);
            long overflow = credit - fitted;
            long forwarded = overflow > 0L ? delegate.insert(what, overflow, mode, source) : 0L;
            if (mode == Actionable.MODULATE) {
                feBuffer += fitted;
            }
            accepted = fitted + forwarded;
        }

        return accepted / costMultiplier;
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    // ---- cell-backed path -------------------------------------------------

    private long extractFromCell(AEKey feKey, MEStorage cell, long needed, Actionable mode, IActionSource source) {
        long available = cell.extract(feKey, needed, Actionable.SIMULATE, source);

        if (mode == Actionable.SIMULATE) {
            long total = available;
            if (total < needed) {
                total += delegate.extract(feKey, needed - total, Actionable.SIMULATE, source);
            }
            return Math.min(needed, total);
        }

        // MODULATE: only touch ME when the cell can't satisfy the request.
        // Refill with ~20 ticks of observed usage + the current demand, so
        // the next window of extracts lands on the cell alone. Not sized to
        // "fill the cell" — a 1 GiB Flux Cell with 50 FE/tick load would pull
        // the whole network otherwise.
        if (available < needed) {
            long pullTarget = recentConsumptionSum() + needed;
            long freeSpace = cell.insert(feKey, Long.MAX_VALUE, Actionable.SIMULATE, source);
            long refill = Math.min(pullTarget, freeSpace);
            if (refill > 0L) {
                long pulled = delegate.extract(feKey, refill, Actionable.MODULATE, source);
                if (pulled > 0L) {
                    long intoCell = cell.insert(feKey, pulled, Actionable.MODULATE, source);
                    long leftover = pulled - intoCell;
                    if (leftover > 0L) {
                        // Cell rejected after simulate accepted (type mismatch
                        // / state race) — put it back so nothing is lost.
                        delegate.insert(feKey, leftover, Actionable.MODULATE, source);
                    }
                }
            }
        }

        long extracted = cell.extract(feKey, needed, Actionable.MODULATE, source);
        if (extracted < needed) {
            extracted += delegate.extract(feKey, needed - extracted, Actionable.MODULATE, source);
        }
        recordConsumption(extracted);
        return extracted;
    }

    private long extractFromRam(AEKey feKey, long needed, Actionable mode, IActionSource source) {
        if (mode == Actionable.SIMULATE) {
            long available = feBuffer;
            if (available < needed) {
                available += delegate.extract(feKey, needed - available, Actionable.SIMULATE, source);
            }
            return Math.min(needed, available);
        }

        if (feBuffer < needed && feBufferCapacity > 0L) {
            long target = computeCacheTarget(needed);
            long missing = Math.max(0L, target - feBuffer);
            if (missing > 0L) {
                feBuffer += delegate.extract(feKey, missing, Actionable.MODULATE, source);
            }
        }

        long actualCost = Math.min(needed, feBuffer);
        if (actualCost <= 0L) {
            return 0L;
        }

        feBuffer -= actualCost;
        recordConsumption(actualCost);
        return actualCost;
    }

    private long computeCacheTarget(long currentDemand) {
        long target = recentConsumptionSum() + currentDemand;
        if (feBufferCapacity > 0L) {
            target = Math.min(target, feBufferCapacity);
        }
        return target;
    }

    // ---- batching API (used mostly by the Pattern Provider path) ---------

    public long preload(AEKey feKey, long amount, IActionSource source) {
        if (amount <= 0L) {
            return 0L;
        }

        long pulled = delegate.extract(feKey, amount, Actionable.MODULATE, source);
        if (pulled <= 0L) {
            return 0L;
        }

        MEStorage cell = resolveCell();
        if (cell != null) {
            long intoCell = cell.insert(feKey, pulled, Actionable.MODULATE, source);
            long leftover = pulled - intoCell;
            if (leftover > 0L) {
                delegate.insert(feKey, leftover, Actionable.MODULATE, source);
                return intoCell;
            }
            return pulled;
        }

        feBuffer += pulled;
        return pulled;
    }

    public long flush(AEKey feKey, IActionSource source) {
        MEStorage cell = resolveCell();
        if (cell != null) {
            // Cell-backed cache is already persistent — nothing to flush on
            // tick boundaries. Trim handles the "don't accumulate" rule.
            return 0L;
        }
        if (feBuffer <= 0L) {
            return 0L;
        }
        long returned = delegate.insert(feKey, feBuffer, Actionable.MODULATE, source);
        feBuffer -= returned;
        return returned;
    }

    /**
     * RAM-mode only: return any FE cached beyond ~{@code recentConsumptionSum}
     * back to the ME network. No-op in cell-backed mode — the cell IS the
     * storage, we never over-pull in the first place (refills are strictly
     * demand-driven, one bulk pull per depletion), so there is nothing to
     * trim.
     */
    public long trimToRecentUsage(AEKey feKey, IActionSource source) {
        if (resolveCell() != null) {
            return 0L;
        }

        long target = recentConsumptionSum();
        if (feBuffer <= target) {
            return 0L;
        }
        long excess = feBuffer - target;
        long returned = delegate.insert(feKey, excess, Actionable.MODULATE, source);
        feBuffer -= returned;
        return returned;
    }

    /**
     * Full flush. Cell-backed mode is already persistent so this is a no-op
     * for the cell side; the RAM buffer is drained back to ME otherwise.
     */
    public long flushAll(AEKey feKey, IActionSource source) {
        MEStorage cell = resolveCell();
        if (cell != null) {
            return 0L;
        }
        return flush(feKey, source);
    }

    // ---- bookkeeping ------------------------------------------------------

    public void advanceHistory() {
        historyPointer = (historyPointer + 1) % consumptionHistory.length;
        consumptionHistory[historyPointer] = 0L;
    }

    public void setCostMultiplier(int multiplier) {
        costMultiplier = Math.max(1, multiplier);
    }

    public int getCostMultiplier() {
        return costMultiplier;
    }

    public void setBufferCapacity(long capacity) {
        feBufferCapacity = Math.max(0L, capacity);
        if (feBufferCapacity > 0L && feBuffer > feBufferCapacity) {
            feBuffer = feBufferCapacity;
        }
    }

    public long getBufferCapacity() {
        return feBufferCapacity;
    }

    public long getBufferedEnergy() {
        MEStorage cell = resolveCell();
        if (cell != null) {
            AEKey feKey = AppFluxBridge.FE_KEY;
            if (feKey == null) {
                return 0L;
            }
            return cell.extract(feKey, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }
        return feBuffer;
    }

    /**
     * Clears the in-memory RAM buffer WITHOUT returning FE to the delegate.
     * No-op when a cell is providing backing storage. Prefer {@link
     * #flushAll(AEKey, IActionSource)} for RAM-mode lifecycle cleanup.
     */
    public void clearBuffer() {
        feBuffer = 0L;
    }

    @Nullable
    private MEStorage resolveCell() {
        return cellSupplier != null ? cellSupplier.get() : null;
    }

    private long recentConsumptionSum() {
        long sum = 0L;
        for (long value : consumptionHistory) {
            sum += value;
        }
        return sum;
    }

    private void recordConsumption(long amount) {
        consumptionHistory[historyPointer] += amount;
    }

    private boolean isFeKey(AEKey what) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        return feKey != null && feKey.equals(what);
    }
}
