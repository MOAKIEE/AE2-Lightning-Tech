package com.moakiee.ae2lt.overload.armor;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import appeng.menu.locator.ItemMenuHostLocator;

public final class OverloadArmorTerminalLocator implements ItemMenuHostLocator {
    private final UUID armorId;
    private final OverloadArmorCarrierLocator carrierLocator;
    private final int terminalSlot;
    private final long sessionVersion;

    public OverloadArmorTerminalLocator(
            UUID armorId,
            OverloadArmorCarrierLocator carrierLocator,
            int terminalSlot,
            long sessionVersion
    ) {
        this.armorId = armorId;
        this.carrierLocator = carrierLocator;
        this.terminalSlot = terminalSlot;
        this.sessionVersion = sessionVersion;
    }

    public UUID armorId() {
        return armorId;
    }

    public OverloadArmorCarrierLocator carrierLocator() {
        return carrierLocator;
    }

    public int terminalSlot() {
        return terminalSlot;
    }

    public long sessionVersion() {
        return sessionVersion;
    }

    public boolean matches(OverloadArmorTerminalLocator other) {
        return armorId.equals(other.armorId)
                && carrierLocator.equals(other.carrierLocator)
                && terminalSlot == other.terminalSlot
                && (sessionVersion == 0L
                        || other.sessionVersion == 0L
                        || sessionVersion == other.sessionVersion);
    }

    @Override
    public ItemStack locateItem(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return OverloadArmorTerminalService.getLiveTerminal(serverPlayer, this);
        }

        return OverloadArmorTerminalService.getClientLiveTerminal(player, this);
    }

    @Override
    public @Nullable BlockHitResult hitResult() {
        return null;
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        carrierLocator.writeToPacket(buf);
        buf.writeVarInt(terminalSlot);
        buf.writeLong(sessionVersion);
    }

    public static OverloadArmorTerminalLocator readFromPacket(FriendlyByteBuf buf) {
        return new OverloadArmorTerminalLocator(
                buf.readUUID(),
                OverloadArmorCarrierLocator.readFromPacket(buf),
                buf.readVarInt(),
                buf.readLong());
    }

    @Override
    public String toString() {
        return "overload armor terminal "
                + armorId
                + " @ "
                + carrierLocator
                + " slot "
                + terminalSlot
                + " session "
                + sessionVersion;
    }
}
