package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MudClodScreenImpactPayloadTest {
    @Test
    void codecPreservesImpactAndSeed() {
        MudClodScreenImpactPayload expected =
                new MudClodScreenImpactPayload(0.78F, 0x4D5544434C4F444CL);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        MudClodScreenImpactPayload.STREAM_CODEC.encode(buffer, expected);
        MudClodScreenImpactPayload actual =
                MudClodScreenImpactPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void intensityIsClampedAtThePacketBoundary() {
        assertEquals(0.0F,
                new MudClodScreenImpactPayload(-2.0F, 1L).intensity());
        assertEquals(1.0F,
                new MudClodScreenImpactPayload(2.0F, 1L).intensity());
    }
}
