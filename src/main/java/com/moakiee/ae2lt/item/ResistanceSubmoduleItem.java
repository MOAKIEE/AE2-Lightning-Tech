package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorFeatureCatalog;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.overload.armor.module.ResistanceSubmodule;

public final class ResistanceSubmoduleItem extends Item implements OverloadArmorSubmoduleItem {

    private static final ResistanceSubmodule INSTANCE = ResistanceSubmodule.INSTANCE;

    static {
        OverloadArmorFeatureCatalog.registerSubmodule(INSTANCE);
    }

    public ResistanceSubmoduleItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public void collectSubmodules(ItemStack stack, Consumer<OverloadArmorSubmodule> output) {
        output.accept(INSTANCE);
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        // 30% post-armor damage resistance per module instance.
        return List.of(new DeviceCapability.DamageMitigation(0.0D, 0.30D));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ae2lt.armor_submodule_resistance.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
