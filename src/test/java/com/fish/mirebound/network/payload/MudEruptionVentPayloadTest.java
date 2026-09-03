package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.mud.SinkingMedium;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MudEruptionVentPayloadTest {
    @Test
    void codecPreservesAdaptiveVisualSource() {
        UUID subLevelId = UUID.fromString("a910e4c7-0c17-4fc7-a6a5-0e85b981fb0f");
        BlockPos supportPos = new BlockPos(34, 78, -19);
        MudEruptionVentPayload expected = new MudEruptionVentPayload(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                17, true, SinkingMedium.MUD,
                1.25D, 64.5D, -8.75D, 7.0F, 91L, -1, 0x123456789ABCDEFL,
                subLevelId, supportPos.asLong(), Direction.WEST);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        MudEruptionVentPayload.STREAM_CODEC.encode(buffer, expected);
        MudEruptionVentPayload actual = MudEruptionVentPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
