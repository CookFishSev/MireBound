package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class AssimilationQteTracePayloadTest {
    @Test
    void codecPreservesSparseTraceEvent() {
        AssimilationQteTracePayload expected = new AssimilationQteTracePayload(
                18, 704, 1, AssimilationQteTracePayload.NODE, 7);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        AssimilationQteTracePayload.STREAM_CODEC.encode(buffer, expected);
        AssimilationQteTracePayload actual =
                AssimilationQteTracePayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
