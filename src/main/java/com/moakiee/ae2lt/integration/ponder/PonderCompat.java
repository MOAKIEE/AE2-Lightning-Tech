package com.moakiee.ae2lt.integration.ponder;

import net.createmod.ponder.foundation.PonderIndex;

public final class PonderCompat {
    private static boolean registered;

    private PonderCompat() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        PonderIndex.addPlugin(new PonderPluginImpl());
        registered = true;
    }
}
