package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.overload.armor.module.OverloadArmorFeatureCatalog;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.overload.armor.module.TestOverloadArmorSubmodule;

/**
 * Installable module that exposes {@link TestOverloadArmorSubmodule} through the armor's module
 * slots. Kept as a standalone item (rather than baked into the armor) so every module in the
 * codebase, including demo ones, goes through the same install/uninstall workbench flow.
 */
public final class TestOverloadArmorSubmoduleItem extends Item implements OverloadArmorSubmoduleItem {
    private static final TestOverloadArmorSubmodule INSTANCE = new TestOverloadArmorSubmodule();

    static {
        // Pre-register so the workbench's on-remove reconcile can find the submodule instance
        // after the item is gone from the slot.
        OverloadArmorFeatureCatalog.registerSubmodule(INSTANCE);
    }

    public TestOverloadArmorSubmoduleItem(Properties properties) {
        // Stackable so the workbench can auto-consume a whole stack via the single input slot;
        // the drain loop stops as soon as the next install would exceed the idle budget, leaving
        // any residual count in the slot for the player to pull back.
        super(properties.stacksTo(64));
    }

    @Override
    public void collectSubmodules(ItemStack stack, Consumer<OverloadArmorSubmodule> output) {
        output.accept(INSTANCE);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("item.ae2lt.test_overload_armor_submodule.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
