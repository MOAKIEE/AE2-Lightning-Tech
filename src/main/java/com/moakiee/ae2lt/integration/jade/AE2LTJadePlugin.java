package com.moakiee.ae2lt.integration.jade;

import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("ae2lt")
public class AE2LTJadePlugin implements IWailaPlugin {
    // Keep AE2LT-owned Jade providers in this package so addon-specific tooltip
    // code stays separate from AE2's own Jade/WTHIT/TOP abstraction layer.
}
