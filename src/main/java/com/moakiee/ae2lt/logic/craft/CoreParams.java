package com.moakiee.ae2lt.logic.craft;

public record CoreParams(int delayTicks, double aePerCopy) {
    public static final int WHEEL_SIZE = 16;
    public static final int WHEEL_MASK = WHEEL_SIZE - 1;

    public CoreParams {
        if (delayTicks < 1 || delayTicks > WHEEL_MASK) {
            throw new IllegalArgumentException("delayTicks must be in [1, " + WHEEL_MASK + "]");
        }
        if (aePerCopy < 0.0D) {
            throw new IllegalArgumentException("aePerCopy must be non-negative");
        }
    }
}
