package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class AssimilationQteInputPayloadTest {
    @Test
    void codecPreservesPressAndReleasePhase() {
        AssimilationQteInputPayload expected = new AssimilationQteInputPayload(
                14, 802, 2, AssimilationQteInputPayload.RELEASE);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        AssimilationQteInputPayload.STREAM_CODEC.encode(buffer, expected);
        AssimilationQteInputPayload actual =
                AssimilationQteInputPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
