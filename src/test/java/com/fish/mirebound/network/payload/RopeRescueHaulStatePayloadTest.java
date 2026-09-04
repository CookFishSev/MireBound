package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RopeRescueHaulStatePayloadTest {
    @Test
    void codecPreservesServerConfirmation() {
        RopeRescueHaulStatePayload expected = new RopeRescueHaulStatePayload(
                12, 4, 29L, true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        RopeRescueHaulStatePayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, RopeRescueHaulStatePayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
