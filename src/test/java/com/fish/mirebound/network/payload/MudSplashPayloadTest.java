package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.mud.SinkingMedium;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MudSplashPayloadTest {
    @Test
    void fountainSpreadFieldsStayAlignedThroughTheCodec() {
        MudSplashPayload.Droplet droplet = new MudSplashPayload.Droplet(
                0.0125F, 0.625F, -0.03125F, 0.0625F,
                true, 0.11F, 4, 3);
        MudSplashPayload expected = new MudSplashPayload(
                1.25D, 64.5D, -3.75D, -1, SinkingMedium.MUD,
                0x1234_5678_9ABCDEFL,
                0.12F, 0.04F, 0.965F, 34, 12345L, List.of(droplet));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        MudSplashPayload.STREAM_CODEC.encode(buffer, expected);
        MudSplashPayload actual = MudSplashPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0x1234_5678_9ABCDEFL, actual.visualSource());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
