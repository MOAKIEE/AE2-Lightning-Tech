package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.module.DashSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorFeatureCatalog;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;

public final class DashSubmoduleItem extends Item implements OverloadArmorSubmoduleItem {

    private static final DashSubmodule INSTANCE = DashSubmodule.INSTANCE;

    static {
        OverloadArmorFeatureCatalog.registerSubmodule(INSTANCE);
    }

    public DashSubmoduleItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public void collectSubmodules(ItemStack stack, Consumer<OverloadArmorSubmodule> output) {
        output.accept(INSTANCE);
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return List.of(new DeviceCapability.DashEffect(1.8D, 60));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ae2lt.armor_submodule_dash.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
