package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.coverage.skin.SkinStainMask;
import com.fish.mirebound.coverage.skin.SkinStainMaskWire;
import io.netty.buffer.Unpooled;
import java.util.BitSet;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class SkinStainPayloadTest {
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.OTHER); }
    @Test void hdMaskAndRemovalSurviveWireRoundTrip() {
        BitSet bits=new BitSet();bits.set(12*256+39);bits.set(256*256-1);
        var mask=new SkinStainMask(256,256,bits);var wire=SkinStainMaskWire.encode(mask);
        var b=buffer();
        try {
            var self=new SelfSkinStainPayload(wire);SelfSkinStainPayload.STREAM_CODEC.encode(b,self);
            assertEquals(mask,SelfSkinStainPayload.STREAM_CODEC.decode(b).mask().decode());
            assertEquals(0,b.readableBytes());
            var sync=new SkinStainSyncPayload(UUID.randomUUID(),wire);SkinStainSyncPayload.STREAM_CODEC.encode(b,sync);
            assertEquals(sync,SkinStainSyncPayload.STREAM_CODEC.decode(b));
            var remove=new SkinStainSyncPayload(sync.owner(),null);SkinStainSyncPayload.STREAM_CODEC.encode(b,remove);
            assertEquals(remove,SkinStainSyncPayload.STREAM_CODEC.decode(b));
        } finally { b.release(); }
    }

    @Test void packetLimitPrecedesAllocationAndExpansionIsBoundedByDimensions() {
        var b=buffer();
        try {
            b.writeVarInt(64);b.writeVarInt(64);b.writeVarInt(SkinStainMaskWire.MAX_COMPRESSED_BYTES+1);
            assertThrows(RuntimeException.class,()->SelfSkinStainPayload.STREAM_CODEC.decode(b));
            BitSet bits=new BitSet();bits.set(0,4096*4096);
            var full=SkinStainMaskWire.encode(new SkinStainMask(4096,4096,bits));
            b.clear();full.write(b);b.readVarInt();b.readVarInt();byte[] compressed=b.readByteArray();
            assertThrows(IllegalArgumentException.class,()->new SkinStainMaskWire(1,1,compressed).decode());
            assertEquals(4096*4096,full.decode().count());
        } finally { b.release(); }
    }
}
