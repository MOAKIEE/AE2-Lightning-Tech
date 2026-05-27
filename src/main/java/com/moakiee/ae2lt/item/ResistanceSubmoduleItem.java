package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.ModuleTooltip;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.overload.armor.module.ResistanceSubmodule;

public final class ResistanceSubmoduleItem extends Item implements OverloadArmorSubmoduleItem {

    private final ResistanceSubmodule submodule;
    private final String tooltipKey;

    public ResistanceSubmoduleItem(
            Properties properties,
            ResistanceSubmodule submodule,
            String tooltipKey) {
        super(properties.stacksTo(1));
        this.submodule = submodule;
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void collectSubmodules(ItemStack stack, Consumer<OverloadArmorSubmodule> output) {
        output.accept(submodule);
    }

    @Override
    public ArmorPart armorPart() {
        return ArmorPart.CHEST;
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return List.of(
                new DeviceCapability.StagedMitigation(submodule.id()),
                new DeviceCapability.PassiveDrain(ArmorOverloadRules.RESISTANCE_PASSIVE_DRAIN_FE));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey)
                .withStyle(ChatFormatting.GRAY));
        ModuleTooltip.appendInstallInfo(this, tooltip);
    }
}
