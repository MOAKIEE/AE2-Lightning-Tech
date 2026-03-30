package com.moakiee.ae2lt.menu;

import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;

public final class Ae2ltSlotSemantics {
    public static final SlotSemantic LIGHTNING_SIMULATION_CATALYST =
            SlotSemantics.register("AE2LT_LIGHTNING_SIMULATION_CATALYST", false);
    public static final SlotSemantic LIGHTNING_COLLECTOR_CRYSTAL =
            SlotSemantics.register("AE2LT_LIGHTNING_COLLECTOR_CRYSTAL", false);
    public static final SlotSemantic TESLA_COIL_DUST =
            SlotSemantics.register("AE2LT_TESLA_COIL_DUST", false);
    public static final SlotSemantic TESLA_COIL_MATRIX =
            SlotSemantics.register("AE2LT_TESLA_COIL_MATRIX", false);
    public static final SlotSemantic ATMOSPHERIC_IONIZER_CONDENSATE =
            SlotSemantics.register("AE2LT_ATMOSPHERIC_IONIZER_CONDENSATE", false);

    private Ae2ltSlotSemantics() {
    }
}
