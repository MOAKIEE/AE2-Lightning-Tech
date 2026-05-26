package com.moakiee.ae2lt.device.overload;

public final class OverloadDynamics {
    public static final int DEFAULT_LOCK_TRIGGER_TICKS = 60;
    public static final int DEFAULT_LOCK_DURATION_TICKS = 600;

    private final int lockTriggerTicks;
    private final int lockDurationTicks;
    private int debtTicks;
    private int lockTicksRemaining;

    public OverloadDynamics() {
        this(DEFAULT_LOCK_TRIGGER_TICKS, DEFAULT_LOCK_DURATION_TICKS);
    }

    public OverloadDynamics(int lockTriggerTicks, int lockDurationTicks) {
        this.lockTriggerTicks = Math.max(1, lockTriggerTicks);
        this.lockDurationTicks = Math.max(1, lockDurationTicks);
    }

    public LockState tick(int currentLoad, int cap) {
        if (lockTicksRemaining > 0) {
            lockTicksRemaining--;
            return state();
        }
        if (cap <= 0 || currentLoad <= cap) {
            debtTicks = 0;
            return LockState.UNLOCKED;
        }
        debtTicks++;
        if (debtTicks >= lockTriggerTicks) {
            debtTicks = 0;
            lockTicksRemaining = lockDurationTicks;
            return LockState.locked(lockTicksRemaining);
        }
        return LockState.debt(debtTicks);
    }

    public LockState state() {
        if (lockTicksRemaining > 0) {
            return LockState.locked(lockTicksRemaining);
        }
        if (debtTicks > 0) {
            return LockState.debt(debtTicks);
        }
        return LockState.UNLOCKED;
    }

    public boolean locked() {
        return lockTicksRemaining > 0;
    }

    public int debtTicks() {
        return debtTicks;
    }

    public int lockTicksRemaining() {
        return lockTicksRemaining;
    }

    public void clear() {
        debtTicks = 0;
        lockTicksRemaining = 0;
    }
}
