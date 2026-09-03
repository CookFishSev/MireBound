package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.rope.RopeFrame;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RopeDragPayloadTest {
    @Test
    void codecCarriesOnlyDragIntentAndOrientation() {
        RopeDragPayload expected = new RopeDragPayload(
                true, 12, 7, RopeFrame.IDENTITY);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        RopeDragPayload.STREAM_CODEC.encode(buffer, expected);
        assertEquals(expected, RopeDragPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void releaseCarriesNoClientPosition() {
        assertEquals(new RopeDragPayload(false, 4, 2, RopeFrame.IDENTITY),
                RopeDragPayload.release(4, 2));
    }
}
