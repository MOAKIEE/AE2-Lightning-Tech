package com.moakiee.ae2lt.item;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.items.storage.StorageCellTooltipComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class FixedInfiniteCellItem extends Item {

    private final Supplier<AEKey> storedKey;

    public FixedInfiniteCellItem(Properties properties, Supplier<AEKey> storedKey) {
        super(properties.stacksTo(1));
        this.storedKey = storedKey;
    }

    public AEKey getStoredKey() {
        return this.storedKey.get();
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.ae2lt.fixed_infinite_cell", getStoredKey().getDisplayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "tooltip.ae2lt.fixed_infinite_cell.infinite").withStyle(ChatFormatting.GREEN));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        var content = Collections.singletonList(new GenericStack(getStoredKey(), getInfiniteAmount()));
        return Optional.of(new StorageCellTooltipComponent(List.of(), content, false, true));
    }

    private long getInfiniteAmount() {
        return (long) Integer.MAX_VALUE * getStoredKey().getAmountPerUnit();
    }
}
