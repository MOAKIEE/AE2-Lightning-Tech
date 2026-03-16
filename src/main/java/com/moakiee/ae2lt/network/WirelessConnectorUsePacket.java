package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WirelessConnectorUsePacket(
        InteractionHand hand,
        BlockPos pos,
        Direction face,
        boolean contiguous
) implements CustomPacketPayload {
    public static final Type<WirelessConnectorUsePacket> TYPE =
            new Type<>(NetworkInit.id("wireless_connector_use"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WirelessConnectorUsePacket> STREAM_CODEC =
            StreamCodec.ofMember(WirelessConnectorUsePacket::write, WirelessConnectorUsePacket::decode);

    @Override
    public Type<WirelessConnectorUsePacket> type() {
        return TYPE;
    }

    public static WirelessConnectorUsePacket decode(RegistryFriendlyByteBuf buf) {
        return new WirelessConnectorUsePacket(
                buf.readEnum(InteractionHand.class),
                buf.readBlockPos(),
                buf.readEnum(Direction.class),
                buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeBlockPos(pos);
        buf.writeEnum(face);
        buf.writeBoolean(contiguous);
    }

    public static void handle(WirelessConnectorUsePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                payload.handleOnServer(player);
            }
        });
    }

    private void handleOnServer(ServerPlayer player) {
        var level = player.level();
        if (!level.isLoaded(pos)) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof OverloadedWirelessConnectorItem)) {
            return;
        }

        var state = level.getBlockState(pos);
        var targetBe = level.getBlockEntity(pos);
        boolean isProvider = state.getBlock() instanceof OverloadedPatternProviderBlock;
        boolean isMachine = targetBe != null;
        if (!isProvider && !isMachine) {
            return;
        }

        if (isProvider) {
            if (targetBe instanceof OverloadedPatternProviderBlockEntity provider
                    && provider.getProviderMode() == OverloadedPatternProviderBlockEntity.ProviderMode.NORMAL) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.connector.need_wireless").withStyle(ChatFormatting.GREEN),
                        true);
                return;
            }

            OverloadedWirelessConnectorItem.selectProvider(stack, level, pos);
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.selected", pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN),
                    true);
            return;
        }

        if (!OverloadedWirelessConnectorItem.hasSelectedProvider(stack)) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.no_provider").withStyle(ChatFormatting.GREEN),
                    true);
            return;
        }

        var provider = OverloadedWirelessConnectorItem.getSelectedProvider(level, stack);
        if (provider == null) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.provider_lost").withStyle(ChatFormatting.GREEN),
                    true);
            OverloadedWirelessConnectorItem.clearSelection(stack);
            return;
        }

        var targets = WirelessConnectorTargetHelper.collectTargets(level, pos, contiguous);
        if (targets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.not_machine").withStyle(ChatFormatting.GREEN),
                    true);
            return;
        }

        var targetDim = level.dimension();
        var disconnected = new ArrayList<BlockPos>();
        var updated = new ArrayList<BlockPos>();
        var connected = new ArrayList<BlockPos>();

        for (var targetPos : targets) {
            var existing = provider.getConnections().stream()
                    .filter(c -> c.sameTarget(targetDim, targetPos))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                if (existing.boundFace() == face) {
                    if (provider.removeConnection(targetDim, targetPos)) {
                        disconnected.add(targetPos.immutable());
                    }
                } else {
                    provider.addOrUpdateConnection(targetDim, targetPos, face);
                    updated.add(targetPos.immutable());
                }
            } else {
                provider.addOrUpdateConnection(targetDim, targetPos, face);
                connected.add(targetPos.immutable());
            }
        }

        if (contiguous && targets.size() > 1) {
            if (!disconnected.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable(
                                        "ae2lt.connector.disconnected_many",
                                        disconnected.size(),
                                        face.getName())
                                .withStyle(ChatFormatting.GREEN),
                        true);
            } else if (!updated.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable(
                                        "ae2lt.connector.updated_many",
                                        updated.size(),
                                        face.getName())
                                .withStyle(ChatFormatting.GREEN),
                        true);
            } else if (!connected.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable(
                                        "ae2lt.connector.connected_many",
                                        connected.size(),
                                        face.getName())
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
            return;
        }

        if (!disconnected.isEmpty()) {
            var single = disconnected.getFirst();
            player.displayClientMessage(
                    Component.translatable("ae2lt.connector.disconnected", single.getX(), single.getY(), single.getZ())
                            .withStyle(ChatFormatting.GREEN),
                    true);
        } else if (!updated.isEmpty()) {
            var single = updated.getFirst();
            player.displayClientMessage(
                    Component.translatable(
                                    "ae2lt.connector.updated",
                                    single.getX(),
                                    single.getY(),
                                    single.getZ(),
                                    face.getName())
                            .withStyle(ChatFormatting.GREEN),
                    true);
        } else if (!connected.isEmpty()) {
            var single = connected.getFirst();
            player.displayClientMessage(
                    Component.translatable(
                                    "ae2lt.connector.connected",
                                    single.getX(),
                                    single.getY(),
                                    single.getZ(),
                                    face.getName())
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
    }
}
