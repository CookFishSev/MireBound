package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RopeRescueHaulPayloadTest {
    @Test
    void codecCarriesTheOperationAndSession() {
        RopeRescueHaulPayload expected = RopeRescueHaulPayload.start(14, 6, 3L, 9L);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        RopeRescueHaulPayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, RopeRescueHaulPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void keepAliveAndStopHaveNoClientSelectedSegment() {
        assertEquals(-1, RopeRescueHaulPayload.keepAlive(14, 3L, 10L).segmentIndex());
        assertEquals(-1, RopeRescueHaulPayload.stop(14, 3L, 11L).segmentIndex());
    }

    @Test
    void codecRejectsUnknownOperation() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
        buffer.writeByte(99);
        buffer.writeVarInt(1);
        buffer.writeVarInt(0);
        buffer.writeLong(1L);
        buffer.writeLong(1L);

        assertThrows(IllegalArgumentException.class,
                () -> RopeRescueHaulPayload.STREAM_CODEC.decode(buffer));
        buffer.release();
    }
}
