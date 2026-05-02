package com.moakiee.ae2lt.logic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.ModList;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;

/**
 * Runtime compatibility shim for ExtendedAE_Plus's "Smart Doubling" feature.
 * <p>
 * EAP wires its smart-doubling marker via a mixin on
 * {@code appeng.helpers.patternprovider.PatternProviderLogic#updatePatterns}
 * (TAIL injection) — but the overload pattern provider fully overrides
 * {@code updatePatterns} and never delegates to {@code super}, so EAP's TAIL
 * never fires. This shim lets the override re-apply the marker manually.
 * <p>
 * All references to EAP types are resolved reflectively so that ae2lt continues
 * to compile and load when EAP is absent.
 */
public final class SmartDoublingCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("ae2lt/SmartDoublingCompat");
    private static final String MOD_ID = "extendedae_plus";

    private record Handles(Class<?> awareClass, Setting<YesNo> setting, MethodHandle setAllowScaling) {}

    private static volatile Handles HANDLES;
    private static volatile boolean INIT_DONE;

    private static Handles handles() {
        if (INIT_DONE) return HANDLES;
        synchronized (SmartDoublingCompat.class) {
            if (INIT_DONE) return HANDLES;
            try {
                if (!ModList.get().isLoaded(MOD_ID)) return null;
                Class<?> awareClass = Class.forName(
                        "com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern");
                Class<?> settingsClass = Class.forName(
                        "com.extendedae_plus.api.config.EAPSettings");
                Field f = settingsClass.getField("SMART_DOUBLING");
                @SuppressWarnings("unchecked")
                Setting<YesNo> setting = (Setting<YesNo>) f.get(null);
                MethodHandle setter = MethodHandles.publicLookup().findVirtual(
                        awareClass,
                        "eap$setAllowScaling",
                        MethodType.methodType(void.class, boolean.class));
                HANDLES = new Handles(awareClass, setting, setter);
                LOGGER.debug("[ae2lt] ExtendedAE_Plus smart-doubling compat wired.");
                return HANDLES;
            } catch (Throwable t) {
                LOGGER.warn("[ae2lt] Failed to wire ExtendedAE_Plus smart-doubling compat: {}",
                        t.toString());
                return null;
            } finally {
                INIT_DONE = true;
            }
        }
    }

    /**
     * Mirror EAP's
     * {@code PatternProviderLogicDoublingMixin#eap$applySmartDoublingToPatterns}
     * for an overload provider whose {@code updatePatterns} fully overrides the
     * vanilla implementation. Call at the end of the override after the
     * {@code patterns} list has been rebuilt.
     */
    public static void applyTo(PatternProviderLogic logic, List<IPatternDetails> patterns) {
        Handles h = handles();
        if (h == null) return;
        boolean allowScaling;
        try {
            allowScaling = logic.getConfigManager().getSetting(h.setting) == YesNo.YES;
        } catch (Throwable t) {
            // Setting not registered (EAP mixin failed to apply on this instance) -- silent no-op.
            return;
        }
        for (IPatternDetails details : patterns) {
            if (h.awareClass.isInstance(details)) {
                try {
                    h.setAllowScaling.invoke(details, allowScaling);
                } catch (Throwable t) {
                    LOGGER.debug("[ae2lt] eap$setAllowScaling invocation failed: {}", t.toString());
                }
            }
        }
    }

    private SmartDoublingCompat() {}
}
