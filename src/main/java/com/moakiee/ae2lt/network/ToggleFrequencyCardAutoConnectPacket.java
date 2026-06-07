package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleFrequencyCardAutoConnectPacket(Optional<InteractionHand> hand) implements CustomPacketPayload {
    public static final Type<ToggleFrequencyCardAutoConnectPacket> TYPE =
            new Type<>(NetworkInit.id("toggle_frequency_card_auto_connect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFrequencyCardAutoConnectPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToggleFrequencyCardAutoConnectPacket::write, ToggleFrequencyCardAutoConnectPacket::decode);

    @Override
    public Type<ToggleFrequencyCardAutoConnectPacket> type() {
        return TYPE;
    }

    public static ToggleFrequencyCardAutoConnectPacket forHand(InteractionHand hand) {
        return new ToggleFrequencyCardAutoConnectPacket(Optional.of(hand));
    }

    public static ToggleFrequencyCardAutoConnectPacket forPreferredCard() {
        return new ToggleFrequencyCardAutoConnectPacket(Optional.empty());
    }

    public static ToggleFrequencyCardAutoConnectPacket decode(RegistryFriendlyByteBuf buf) {
        return new ToggleFrequencyCardAutoConnectPacket(
                buf.readBoolean()
                        ? Optional.of(buf.readEnum(InteractionHand.class))
                        : Optional.empty());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(hand.isPresent());
        hand.ifPresent(value -> buf.writeEnum(value));
    }

    public static void handle(ToggleFrequencyCardAutoConnectPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                payload.handleOnServer(player);
            }
        });
    }

    private void handleOnServer(ServerPlayer player) {
        ItemStack stack;
        if (hand.isPresent()) {
            stack = player.getItemInHand(hand.get());
        } else {
            var selection = OverloadedFrequencyCardItem.selectToggleCard(player);
            if (selection.ambiguous()) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.frequency_card.auto_ambiguous")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            stack = selection.selected().orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.frequency_card.no_toggle_candidate")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
        }
        if (!(stack.getItem() instanceof OverloadedFrequencyCardItem)) return;

        var data = OverloadedFrequencyCardItem.getData(stack);
        if (data.isBound() && !data.canBeUsedBy(player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.frequency_card.card_owner_mismatch")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        boolean enabled = OverloadedFrequencyCardItem.toggleAutoConnect(stack);
        player.displayClientMessage(
                Component.translatable(enabled
                                ? "ae2lt.frequency_card.auto_enabled"
                                : "ae2lt.frequency_card.auto_disabled")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                true);
    }
}
