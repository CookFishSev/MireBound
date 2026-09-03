package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.mud.SinkingMedium;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MudProbeBubblePayloadTest {
    @Test
    void codecPreservesProbePointSurfaceAndMedium() {
        MudProbeBubblePayload expected = new MudProbeBubblePayload(
                List.of(
                        new MudProbeBubblePayload.BubbleSpawn(
                                12.125D, 63.875D, -4.5D, 1),
                        new MudProbeBubblePayload.BubbleSpawn(
                                12.25D, 63.8125D, -4.5D, 5),
                        new MudProbeBubblePayload.BubbleSpawn(
                                12.0625D, 63.75D, -4.5D, 10)),
                new Vec3(0.6D, 0.8D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                SinkingMedium.PEAT_BOG,
                new BlockPos(12, 63, -5));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        MudProbeBubblePayload.STREAM_CODEC.encode(buffer, expected);
        MudProbeBubblePayload actual = MudProbeBubblePayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
