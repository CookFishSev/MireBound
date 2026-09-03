package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class EntityMudCoveragePayloadTest {
    @Test
    void codecPreservesCompleteCoverageSnapshot() {
        EntityMudCoveragePayload expected = new EntityMudCoveragePayload(
                37, UUID.fromString("c571aa6b-65e8-4ead-bab2-14cedcc7416f"),
                12, 0x13572468, 181, 4, 0x123456789ABCDEFL,
                72, 9, Long.MIN_VALUE | 71L,
                203,
                true,
                List.of(
                        new EntityMudCoveragePayload.Spot(
                                7, 0, 17, 219, 64, 51, 188, 4, 29L),
                        new EntityMudCoveragePayload.Spot(
                                19, 1, 128, 8, 128, 255, 77, 9,
                                Long.MIN_VALUE | 3L),
                        new EntityMudCoveragePayload.Spot(
                                23, 3, 35, 144, 219, 92, 166, 2,
                                0x5A5A5A5AL)),
                List.of());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        EntityMudCoveragePayload.STREAM_CODEC.encode(buffer, expected);
        EntityMudCoveragePayload actual =
                EntityMudCoveragePayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void codecPreservesIncrementalChangesAndRemovals() {
        EntityMudCoveragePayload expected = new EntityMudCoveragePayload(
                9, UUID.fromString("a483f270-7a72-4630-ab83-1820528288fe"),
                41, 99, 33, 2, 71L,
                0, 15, 0L, 255, false,
                List.of(new EntityMudCoveragePayload.Spot(
                        28, 0, 201, 31, 94, 61, 115, 2, 81L)),
                List.of(3, 17));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        EntityMudCoveragePayload.STREAM_CODEC.encode(buffer, expected);
        EntityMudCoveragePayload actual =
                EntityMudCoveragePayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
