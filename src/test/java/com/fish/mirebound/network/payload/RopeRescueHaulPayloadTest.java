package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RopeRescueHaulPayloadTest {
    @Test
    void codecCarriesOnlyTheVisibleHaulTarget() {
        RopeRescueHaulPayload expected = new RopeRescueHaulPayload(true, 14, 6);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        RopeRescueHaulPayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, RopeRescueHaulPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
