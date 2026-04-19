package com.moakiee.ae2lt.overload.armor;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import appeng.menu.locator.ItemMenuHostLocator;

public final class OverloadArmorMenuLocator implements ItemMenuHostLocator {
    private final UUID armorId;
    private final OverloadArmorCarrierLocator carrierLocator;

    public OverloadArmorMenuLocator(UUID armorId, OverloadArmorCarrierLocator carrierLocator) {
        this.armorId = armorId;
        this.carrierLocator = carrierLocator;
    }

    public UUID armorId() {
        return armorId;
    }

    public OverloadArmorCarrierLocator carrierLocator() {
        return carrierLocator;
    }

    public boolean matches(OverloadArmorMenuLocator other) {
        return armorId.equals(other.armorId) && carrierLocator.equals(other.carrierLocator);
    }

    public boolean isEquipped(Player player) {
        return carrierLocator.resolve(player, armorId) != null;
    }

    @Override
    public ItemStack locateItem(Player player) {
        var carrier = carrierLocator.resolve(player, armorId);
        return carrier != null ? carrier.armorStack() : ItemStack.EMPTY;
    }

    @Override
    public @Nullable BlockHitResult hitResult() {
        return null;
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        carrierLocator.writeToPacket(buf);
    }

    public static OverloadArmorMenuLocator readFromPacket(FriendlyByteBuf buf) {
        return new OverloadArmorMenuLocator(buf.readUUID(), OverloadArmorCarrierLocator.readFromPacket(buf));
    }

    @Override
    public String toString() {
        return "overload armor menu " + armorId + " @ " + carrierLocator;
    }
}
