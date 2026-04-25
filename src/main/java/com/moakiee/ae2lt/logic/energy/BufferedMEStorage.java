package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

import com.glodblock.github.appflux.common.me.cell.FluxCellInventory;

/**
 * FE cache/proxy in front of an ME storage delegate.
 *
 * <p>Operates in one of three modes:
 * <ul>
 * <li><b>Overload staging</b>: {@link #beginCellBatch(AEKey, IActionSource)}
 *     moves the installed Flux Cell into a long cache, direct sends only touch
 *     that long, and {@link #refillForDirectSend(long, IActionSource)} is the
 *     only path that refills from ME.</li>
 * <li><b>Normal batch</b>: {@link #beginMemoryBatch(AEKey, long, IActionSource)}
 *     performs a single ME extraction into {@code feBuffer}; sends consume that
 *     buffer and {@link #endBatch(AEKey, IActionSource)} returns the remainder.</li>
 * <li><b>Pass-through</b>: when no cell and no transient preload are present,
 *     every call forwards directly to the ME delegate with no caching.</li>
 * </ul>
 */
public class BufferedMEStorage implements MEStorage {

    private static final int HISTORY_SIZE = 20;

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }

    private static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) return 0L;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private final MEStorage delegate;
    @Nullable
    private final Supplier<MEStorage> cellSupplier;
    @Nullable
    private final Runnable cellPersistCallback;
    private final long[] consumptionHistory = new long[HISTORY_SIZE];

    private long feBuffer; // transient preload buffer only
    @Nullable
    private MEStorage stagedCell;
    private long stagedCellFe;
    private long stagedCellCapacity;
    private boolean batchOnly;
    private int historyPointer;
    private int costMultiplier = 1;

    public BufferedMEStorage(MEStorage delegate) {
        this(delegate, null);
    }

    /**
     * @param delegate     ME-network storage to refill from / flush to.
     * @param cellSupplier live getter for the Flux Cell's {@link MEStorage}
     *                     (the cell currently installed in the host). Return
     *                     {@code null} when no valid cell is present; the class
     *                     will pass through to the ME delegate unless explicitly
     *                     preloaded.
     */
    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier) {
        this(delegate, cellSupplier, null);
    }

    public BufferedMEStorage(MEStorage delegate, @Nullable Supplier<MEStorage> cellSupplier,
                             @Nullable Runnable cellPersistCallback) {
        this.delegate = delegate;
        this.cellSupplier = cellSupplier;
        this.cellPersistCallback = cellPersistCallback;
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

        long needed = saturatingMul(amount, costMultiplier);

        if (batchOnly || feBuffer > 0L) {
            return extractFromBuffer(needed, mode) / costMultiplier;
        }

        MEStorage cell = resolveCell();
        if (cell != null) {
            if (cell == stagedCell) {
                return extractFromStagedCell(needed, mode) / costMultiplier;
            }
            return 0L;
        }

        return delegate.extract(feKey, needed, mode, source) / costMultiplier;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!isFeKey(what) || amount <= 0) {
            return delegate.insert(what, amount, mode, source);
        }

        long credit = saturatingMul(amount, costMultiplier);
        long accepted;

        if (batchOnly || feBuffer > 0L) {
            accepted = credit;
            if (mode == Actionable.MODULATE && credit > 0L) {
                feBuffer = saturatingAdd(feBuffer, credit);
                recordReturn(credit);
            }
        } else {
            MEStorage cell = resolveCell();
            if (cell != null && cell == stagedCell) {
                long intoStage = mode == Actionable.SIMULATE
                        ? Math.min(credit, stagedCellFreeSpace())
                        : insertIntoStagedCell(credit);
                long overflow = credit - intoStage;
                long intoMe = overflow > 0L ? delegate.insert(what, overflow, mode, source) : 0L;
                accepted = saturatingAdd(intoStage, intoMe);
                if (mode == Actionable.MODULATE && intoStage > 0L) {
                    recordReturn(intoStage);
                }
            } else {
                accepted = delegate.insert(what, credit, mode, source);
            }
        }

        return accepted / costMultiplier;
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    public long refillBudgetForDirectSend(long currentDemand) {
        if (currentDemand <= 0L || stagedCell == null || batchOnly) {
            return 0L;
        }

        long currentCost = saturatingMul(currentDemand, costMultiplier);
        return saturatingAdd(currentCost, recentConsumptionBeforeCurrentTick());
    }

    public void refillForDirectSend(long refillBudget, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || refillBudget <= 0L || stagedCell == null || batchOnly) {
            return;
        }

        refillStagedCell(feKey, refillBudget, source);
    }

    public long extractForDirectSend(long amount, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || amount <= 0L) {
            return 0L;
        }

        long needed = saturatingMul(amount, costMultiplier);
        long extracted;
        if (stagedCell != null) {
            extracted = Math.min(needed, stagedCellFe);
            stagedCellFe -= extracted;
            recordConsumption(extracted);
        } else if (batchOnly || feBuffer > 0L) {
            extracted = Math.min(needed, feBuffer);
            feBuffer -= extracted;
            if (extracted > 0L) {
                recordConsumption(extracted);
            }
        } else {
            return 0L;
        }
        return extracted / costMultiplier;
    }

    public long returnFromDirectSend(long amount, IActionSource source) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || amount <= 0L) {
            return 0L;
        }

        long credit = saturatingMul(amount, costMultiplier);
        long accepted;
        if (stagedCell != null) {
            accepted = Math.min(credit, stagedCellFreeSpace());
            stagedCellFe = saturatingAdd(stagedCellFe, accepted);
        } else if (batchOnly || feBuffer > 0L) {
            feBuffer = saturatingAdd(feBuffer, credit);
            accepted = credit;
        } else {
            accepted = delegate.insert(feKey, credit, Actionable.MODULATE, source);
        }

        if (accepted > 0L) {
            recordReturn(accepted);
        }
        return accepted / costMultiplier;
    }

    // ---- cell-backed path -------------------------------------------------

    public boolean beginCellBatch(AEKey feKey, IActionSource source) {
        if (!isFeKey(feKey)) {
            return false;
        }
        if (stagedCell != null) {
            return true;
        }
        MEStorage cell = resolveCell();
        if (cell == null) {
            return false;
        }

        stagedCell = cell;
        stagedCellFe = cell.extract(feKey, Long.MAX_VALUE, Actionable.MODULATE, source);
        stagedCellCapacity = Math.max(stagedCellFe, cell.insert(feKey, Long.MAX_VALUE, Actionable.SIMULATE, source));
        return true;
    }

    public long endCellBatch(AEKey feKey, IActionSource source) {
        return endCellBatch(feKey, source, true);
    }

    public long endCellBatch(AEKey feKey, IActionSource source, boolean persist) {
        MEStorage cell = stagedCell;
        if (cell == null) {
            return 0L;
        }
        if (!persist) {
            return 0L;
        }

        try {
            long toPersist = stagedCellCapacity > 0L ? Math.min(stagedCellFe, stagedCellCapacity) : stagedCellFe;
            long intoCell = toPersist > 0L ? cell.insert(feKey, toPersist, Actionable.MODULATE, source) : 0L;
            stagedCellFe -= intoCell;
            long returned = stagedCellFe > 0L
                    ? delegate.insert(feKey, stagedCellFe, Actionable.MODULATE, source)
                    : 0L;
            stagedCellFe -= returned;
            if (cell instanceof FluxCellInventory fluxCell) {
                fluxCell.persist();
            }
            return intoCell;
        } finally {
            if (cellPersistCallback != null) {
                cellPersistCallback.run();
            }
            stagedCell = null;
            stagedCellFe = 0L;
            stagedCellCapacity = 0L;
        }
    }

    private long extractFromStagedCell(long needed, Actionable mode) {
        long available = stagedCellFe;

        if (mode == Actionable.SIMULATE) {
            return Math.min(needed, available);
        }

        long fromStage = Math.min(needed, stagedCellFe);
        stagedCellFe -= fromStage;
        recordConsumption(fromStage);
        return fromStage;
    }

    private void refillStagedCell(AEKey feKey, long needed, IActionSource source) {
        long refill = Math.min(needed, stagedCellFreeSpace());
        if (refill > 0L) {
            long pulled = delegate.extract(feKey, refill, Actionable.MODULATE, source);
            stagedCellFe = saturatingAdd(stagedCellFe, pulled);
        }
    }

    private long insertIntoStagedCell(long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long accepted = Math.min(amount, stagedCellFreeSpace());
        stagedCellFe = saturatingAdd(stagedCellFe, accepted);
        return accepted;
    }

    private long stagedCellFreeSpace() {
        return stagedCellCapacity > stagedCellFe ? stagedCellCapacity - stagedCellFe : 0L;
    }

    private long extractFromBuffer(long needed, Actionable mode) {
        if (mode == Actionable.SIMULATE) {
            return Math.min(needed, feBuffer);
        }

        long extracted = Math.min(needed, feBuffer);
        feBuffer -= extracted;
        if (extracted > 0L) {
            recordConsumption(extracted);
        }
        return extracted;
    }

    public long beginMemoryBatch(AEKey feKey, long amount, IActionSource source) {
        batchOnly = true;
        if (amount <= 0L) {
            return 0L;
        }

        long pulled = delegate.extract(feKey, amount, Actionable.MODULATE, source);
        if (pulled > 0L) {
            feBuffer = saturatingAdd(feBuffer, pulled);
        }
        return pulled;
    }

    public long endBatch(AEKey feKey, IActionSource source) {
        try {
            return flush(feKey, source);
        } finally {
            batchOnly = false;
        }
    }

    public long flush(AEKey feKey, IActionSource source) {
        if (feBuffer <= 0L) {
            return 0L;
        }
        long returned = delegate.insert(feKey, feBuffer, Actionable.MODULATE, source);
        feBuffer -= returned;
        return returned;
    }

    /**
     * Full flush. Cell-backed mode is already persistent so this is a no-op
     * for the cell side; the transient preload buffer is drained back to ME
     * otherwise.
     */
    public long flushAll(AEKey feKey, IActionSource source) {
        if (stagedCell != null) {
            endCellBatch(feKey, source, true);
            return 0L;
        }
        MEStorage cell = resolveCell();
        if (cell != null) {
            if (cell instanceof FluxCellInventory fluxCell) {
                fluxCell.persist();
            }
            if (cellPersistCallback != null) {
                cellPersistCallback.run();
            }
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

    public long getBufferedEnergy() {
        if (stagedCell != null) {
            return stagedCellFe;
        }
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
     * Clears the transient preload buffer WITHOUT returning FE to the delegate.
     * No-op when a cell is providing backing storage. Prefer {@link
     * #flushAll(AEKey, IActionSource)} for lifecycle cleanup.
     */
    public void clearBuffer() {
        feBuffer = 0L;
    }

    @Nullable
    private MEStorage resolveCell() {
        return cellSupplier != null ? cellSupplier.get() : null;
    }

    private long recentConsumptionBeforeCurrentTick() {
        long sum = 0L;
        for (int i = 0; i < consumptionHistory.length; i++) {
            if (i != historyPointer) {
                sum = saturatingAdd(sum, consumptionHistory[i]);
            }
        }
        return sum;
    }

    private void recordConsumption(long amount) {
        consumptionHistory[historyPointer] = saturatingAdd(consumptionHistory[historyPointer], amount);
    }

    private void recordReturn(long amount) {
        consumptionHistory[historyPointer] = Math.max(0L, consumptionHistory[historyPointer] - amount);
    }

    private boolean isFeKey(AEKey what) {
        AEKey feKey = AppFluxBridge.FE_KEY;
        return feKey != null && feKey.equals(what);
    }
}
