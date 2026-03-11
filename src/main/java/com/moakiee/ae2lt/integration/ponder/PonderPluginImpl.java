package com.moakiee.ae2lt.integration.ponder;

import com.moakiee.ae2lt.AE2LightningTech;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class PonderPluginImpl implements PonderPlugin {
    @Override
    public String getModId() {
        return AE2LightningTech.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderScenes.register(helper);
    }
}
