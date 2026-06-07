package com.moakiee.ae2lt.logic;

public final class OverloadedInterfaceTickDecider {
    private static final int ALL_NORMAL_DIRECTIONS = 6;

    private OverloadedInterfaceTickDecider() {
    }

    public static boolean hasServerTickWork(
            boolean wirelessMode,
            boolean hasImportBuffer,
            boolean hasWirelessConnections,
            boolean importAuto,
            boolean exportAuto,
            boolean hasEnergyOutput,
            boolean hasFeKey,
            boolean hasInductionCard) {
        if (hasImportBuffer) {
            return true;
        }

        if (wirelessMode) {
            if (hasWirelessConnections && (importAuto || exportAuto)) {
                return true;
            }
        } else if (importAuto || exportAuto) {
            return true;
        }

        return ((wirelessMode && hasWirelessConnections) || hasEnergyOutput)
                && hasFeKey
                && hasInductionCard;
    }

    public static int normalIoDirectionCount(boolean hasConfiguredDirection) {
        return hasConfiguredDirection ? 1 : ALL_NORMAL_DIRECTIONS;
    }

    public static boolean shouldRegisterEjectPorts(boolean wirelessMode, boolean ejectMode) {
        return wirelessMode && ejectMode;
    }
}
