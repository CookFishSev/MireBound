package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.Unpooled;
import com.fish.mirebound.generation.MudTerrainGenerationRequest;
import com.fish.mirebound.generation.MudTerrainGenerationSettings;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.generation.MudTerrainRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MudTerrainGenerationPayloadTest {
    @Test
    void codecPreservesDeterministicGenerationParameters() {
        MudTerrainGenerationPayload expected = new MudTerrainGenerationPayload(
                MudTerrainGenerationPayload.Action.GENERATE,
                new MudTerrainGenerationRequest(
                        MudTerrainGenerationType.LAKE_SURFACE,
                        new BlockPos(-471, 83, 902), true,
                        new MudTerrainGenerationSettings(
                                31, 7, 0.625D, 14, 887231, true),
                        new MudTerrainLakeSettings(
                                13, 6, 991231,
                                net.minecraft.resources.ResourceLocation.parse(
                                        "minecraft:deepslate"),
                                net.minecraft.resources.ResourceLocation.parse(
                                        "mirebound:mud"), 11, false),
                        new MudTerrainRotation(
                                Direction.SOUTH, Direction.UP, Direction.WEST)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        MudTerrainGenerationPayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, MudTerrainGenerationPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void codecRetainsInvalidWireStatusAfterClampingValues() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
        buffer.writeVarInt(MudTerrainGenerationPayload.Action.GENERATE.ordinal());
        buffer.writeVarInt(99);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeBoolean(true);
        buffer.writeVarInt(99);
        buffer.writeVarInt(0);
        buffer.writeDouble(2.0D);
        buffer.writeVarInt(99);
        buffer.writeVarInt(-1);
        buffer.writeBoolean(false);
        buffer.writeVarInt(99);
        buffer.writeVarInt(0);
        buffer.writeVarInt(-1);
        buffer.writeResourceLocation(MudTerrainLakeSettings.AIR);
        buffer.writeResourceLocation(MudTerrainLakeSettings.AIR);
        buffer.writeVarInt(17);
        buffer.writeBoolean(true);
        buffer.writeVarInt(99);
        buffer.writeVarInt(Direction.UP.get3DDataValue());
        buffer.writeVarInt(Direction.SOUTH.get3DDataValue());

        MudTerrainGenerationPayload decoded =
                MudTerrainGenerationPayload.STREAM_CODEC.decode(buffer);

        assertFalse(decoded.request().validWireValues());
        assertEquals(48, decoded.request().depositSettings().radius());
        assertEquals(24, decoded.request().lakeSettings().horizontalRadius());
        assertEquals(16, decoded.request().lakeSettings().surfaceHeightPixels());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void invalidActionDoesNotFallbackToGenerationOrUndo() {
        MudTerrainGenerationPayload invalid = new MudTerrainGenerationPayload(
                MudTerrainGenerationPayload.Action.INVALID,
                new MudTerrainGenerationRequest(
                        MudTerrainGenerationType.LAKE_SURFACE,
                        BlockPos.ZERO, false,
                        new MudTerrainGenerationSettings(
                                12, 3, 0.55D, 6, 92821, false),
                        new MudTerrainLakeSettings(
                                8, 4, 92821,
                                MudTerrainLakeSettings.AIR,
                                MudTerrainLakeSettings.AIR),
                        MudTerrainRotation.IDENTITY));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        MudTerrainGenerationPayload.STREAM_CODEC.encode(buffer, invalid);

        assertEquals(MudTerrainGenerationPayload.Action.INVALID,
                MudTerrainGenerationPayload.STREAM_CODEC.decode(buffer).action());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
