package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RopeClimbInputPayloadTest {
    @Test
    void codecCarriesOnlyJumpAndCrouchIntent() {
        RopeClimbInputPayload expected = new RopeClimbInputPayload(true, true, false);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        RopeClimbInputPayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, RopeClimbInputPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
