package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.overload.armor.module.ReflectSubmodule;

public final class ReflectSubmoduleItem extends Item implements OverloadArmorSubmoduleItem {

    private static final ReflectSubmodule INSTANCE = ReflectSubmodule.INSTANCE;

    public ReflectSubmoduleItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public void collectSubmodules(ItemStack stack, Consumer<OverloadArmorSubmodule> output) {
        output.accept(INSTANCE);
    }

    @Override
    public ArmorPart armorPart() {
        return ArmorPart.CHEST;
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return List.of(
                new DeviceCapability.ReflectTuning(0.30D, 30_000L, 2),
                new DeviceCapability.PassiveDrain(ArmorOverloadRules.REFLECT_PASSIVE_DRAIN_FE));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ae2lt.chestplate_module_reflect.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
