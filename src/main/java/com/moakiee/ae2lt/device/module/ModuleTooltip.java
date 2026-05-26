package com.moakiee.ae2lt.device.module;

import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.moakiee.ae2lt.device.DeviceKind;

public final class ModuleTooltip {
    private ModuleTooltip() {
    }

    public static void appendInstallInfo(OverloadDeviceModuleItem module, List<Component> tooltip) {
        if (module == null) {
            return;
        }
        tooltip.add(Component.translatable(
                "ae2lt.module.tooltip.installable_on",
                deviceList(module)).withStyle(ChatFormatting.GRAY));
    }

    private static Component deviceList(OverloadDeviceModuleItem module) {
        MutableComponent out = Component.empty();
        boolean[] first = {true};
        module.acceptableDevices().stream()
                .sorted(Comparator.comparing(DeviceKind::name))
                .forEach(kind -> {
                    if (!first[0]) {
                        out.append(Component.literal(", "));
                    }
                    out.append(Component.translatable(deviceKey(kind)));
                    first[0] = false;
                });
        return out;
    }

    private static String deviceKey(DeviceKind kind) {
        return "ae2lt.module.device." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }

}
