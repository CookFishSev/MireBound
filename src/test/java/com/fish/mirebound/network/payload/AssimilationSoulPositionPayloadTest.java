package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class AssimilationSoulPositionPayloadTest {
    @Test
    void codecPreservesSoulCameraPosition() {
        AssimilationSoulPositionPayload expected = new AssimilationSoulPositionPayload(
                12.25F, 67.5F, -4.75F);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        AssimilationSoulPositionPayload.STREAM_CODEC.encode(buffer, expected);
        AssimilationSoulPositionPayload actual =
                AssimilationSoulPositionPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
