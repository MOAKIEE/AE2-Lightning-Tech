package com.moakiee.ae2lt.machine.atmosphericionizer;

import java.util.Objects;

import net.neoforged.neoforge.energy.IEnergyStorage;

public final class AtmosphericIonizerEnergyStorage implements IEnergyStorage {
    private final long capacity;
    private final long maxReceivePerOperation;
    private final Runnable changeListener;

    private long storedEnergy;

    public AtmosphericIonizerEnergyStorage(long capacity, long maxReceivePerOperation, Runnable changeListener) {
        if (capacity <= 0L) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (maxReceivePerOperation <= 0L) {
            throw new IllegalArgumentException("maxReceivePerOperation must be positive");
        }

        this.capacity = capacity;
        this.maxReceivePerOperation = maxReceivePerOperation;
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) {
            return 0;
        }

        long accepted = Math.min(
                Math.min(Integer.toUnsignedLong(maxReceive), maxReceivePerOperation),
                capacity - storedEnergy);
        if (accepted <= 0L) {
            return 0;
        }

        if (!simulate) {
            storedEnergy += accepted;
            changeListener.run();
        }

        return (int) accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    public int extractInternal(long amount, boolean simulate) {
        if (amount <= 0L) {
            return 0;
        }

        long extracted = Math.min(storedEnergy, Math.min(amount, Integer.MAX_VALUE));
        if (extracted <= 0L) {
            return 0;
        }

        if (!simulate) {
            storedEnergy -= extracted;
            changeListener.run();
        }

        return (int) extracted;
    }

    public long getStoredEnergyLong() {
        return storedEnergy;
    }

    public long getCapacityLong() {
        return capacity;
    }

    public void loadStoredEnergy(long storedEnergy) {
        this.storedEnergy = Math.max(0L, Math.min(storedEnergy, capacity));
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, storedEnergy);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
