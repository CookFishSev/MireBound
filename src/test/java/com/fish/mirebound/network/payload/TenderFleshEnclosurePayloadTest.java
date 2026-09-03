package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class TenderFleshEnclosurePayloadTest {
    @Test
    void codecPreservesPerPillarDurability() {
        TenderFleshEnclosurePayload expected = new TenderFleshEnclosurePayload(
                42, true, false, 0x05, 0x0A3, 0xDB6,
                120, 1.0F,
                10.25D, 64.5D, -8.75D,
                10.5D, -8.5D);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        TenderFleshEnclosurePayload.STREAM_CODEC.encode(buffer, expected);
        TenderFleshEnclosurePayload actual =
                TenderFleshEnclosurePayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
