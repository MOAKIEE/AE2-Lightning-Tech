package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AE2LightningTech.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadedPatternProviderMenu>>
            OVERLOADED_PATTERN_PROVIDER = MENU_TYPES.register(
                    "overloaded_pattern_provider",
                    () -> OverloadedPatternProviderMenu.TYPE);

    private ModMenuTypes() {
    }
}
