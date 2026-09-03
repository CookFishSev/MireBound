package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RopeInteractionReleasePayloadTest {
    @Test
    void codecCarriesTheValidatedInteractionThatWasReleased() {
        RopeInteractionReleasePayload expected =
                new RopeInteractionReleasePayload(14, true, 6);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        RopeInteractionReleasePayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, RopeInteractionReleasePayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
