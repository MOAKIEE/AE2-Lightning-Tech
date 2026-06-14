package com.moakiee.ae2lt.api.client;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side extension point for hiding left-toolbar buttons that other mods
 * inject into AE2LT's overloaded pattern provider screens.
 */
public final class PatternProviderToolbarButtonHider {
    public static final String EXTENDED_AE_PLUS_SERVER_SETTING_BUTTON =
            "com.extendedae_plus.client.gui.widgets.EAPServerSettingToggleButton";

    private static final Set<String> HIDDEN_BUTTON_CLASS_NAMES = ConcurrentHashMap.newKeySet();

    static {
        registerHiddenButtonClassName(EXTENDED_AE_PLUS_SERVER_SETTING_BUTTON);
    }

    public static void registerHiddenButtonClassName(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className must not be blank");
        }
        HIDDEN_BUTTON_CLASS_NAMES.add(className);
    }

    public static boolean shouldHideToolbarButtonClassName(String className) {
        return HIDDEN_BUTTON_CLASS_NAMES.contains(className);
    }

    public static int removeHiddenToolbarButtons(List<?> buttons) {
        int previousSize = buttons.size();
        buttons.removeIf(button -> button != null
                && shouldHideToolbarButtonClassName(button.getClass().getName()));
        return previousSize - buttons.size();
    }

    private PatternProviderToolbarButtonHider() {
    }
}
