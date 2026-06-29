package com.moakiee.ae2lt.logic.timewheelcpu;

public final class TimeWheelFastPlanningGate {
    private TimeWheelFastPlanningGate() {
    }

    public static boolean shouldEnableFastPlanning(Iterable<? extends CpuState> cpus) {
        for (var cpu : cpus) {
            if (cpu.isActive()) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface CpuState {
        boolean isActive();
    }
}
