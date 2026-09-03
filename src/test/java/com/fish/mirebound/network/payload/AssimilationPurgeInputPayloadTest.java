package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class AssimilationPurgeInputPayloadTest {
    @Test
    void codecDistinguishesToggleFromMovementAttempt() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
        AssimilationPurgeInputPayload.STREAM_CODEC.encode(
                buffer, new AssimilationPurgeInputPayload(true));

        AssimilationPurgeInputPayload decoded =
                AssimilationPurgeInputPayload.STREAM_CODEC.decode(buffer);

        assertEquals(true, decoded.movementAttempt());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void codecPreservesToggleIntent() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
        AssimilationPurgeInputPayload.STREAM_CODEC.encode(
                buffer, new AssimilationPurgeInputPayload(false));

        AssimilationPurgeInputPayload decoded =
                AssimilationPurgeInputPayload.STREAM_CODEC.decode(buffer);

        assertEquals(false, decoded.movementAttempt());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
