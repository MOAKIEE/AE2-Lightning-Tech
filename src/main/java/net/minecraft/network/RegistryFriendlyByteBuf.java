package net.minecraft.network;

import io.netty.buffer.ByteBuf;

/**
 * Minimal 1.21 type alias used by code that does not require registry-aware
 * lookup during the porting pass.
 */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    public RegistryFriendlyByteBuf(ByteBuf source) {
        super(source);
    }
}
