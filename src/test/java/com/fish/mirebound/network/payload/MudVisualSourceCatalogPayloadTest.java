package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MudVisualSourceCatalogPayloadTest {
    @Test
    void codecPreservesSourceStatePairs() {
        MudVisualSourceCatalogPayload expected = new MudVisualSourceCatalogPayload(
                11, new long[] {1L, 2L, 3L}, new int[] {7, 12, 19});
        RegistryFriendlyByteBuf buffer = buffer();

        MudVisualSourceCatalogPayload.STREAM_CODEC.encode(buffer, expected);
        MudVisualSourceCatalogPayload actual =
                MudVisualSourceCatalogPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected.entityId(), actual.entityId());
        assertArrayEquals(expected.sources(), actual.sources());
        assertArrayEquals(expected.stateIds(), actual.stateIds());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void mismatchedArraysAreTruncatedBeforeEncoding() {
        MudVisualSourceCatalogPayload payload = new MudVisualSourceCatalogPayload(
                1, new long[] {4L, 5L}, new int[] {8});
        RegistryFriendlyByteBuf buffer = buffer();

        MudVisualSourceCatalogPayload.STREAM_CODEC.encode(buffer, payload);
        MudVisualSourceCatalogPayload decoded =
                MudVisualSourceCatalogPayload.STREAM_CODEC.decode(buffer);

        assertArrayEquals(new long[] {4L}, decoded.sources());
        assertArrayEquals(new int[] {8}, decoded.stateIds());
        buffer.release();
    }

    @Test
    void decoderRejectsAnOversizedCatalogBeforeAllocatingIt() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeVarInt(1);
        buffer.writeVarInt(MudVisualSourceCatalogPayload.MAX_ENTRIES + 1);

        assertThrows(IllegalArgumentException.class,
                () -> MudVisualSourceCatalogPayload.STREAM_CODEC.decode(buffer));
        buffer.release();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
