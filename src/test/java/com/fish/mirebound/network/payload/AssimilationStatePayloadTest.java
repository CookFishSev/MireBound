package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.assimilation.AssimilationQteAction;
import com.fish.mirebound.assimilation.AssimilationStage;
import com.fish.mirebound.mud.SinkingMedium;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class AssimilationStatePayloadTest {
    @Test
    void codecPreservesStateProfileAndRevealMask() {
        byte[] revealed = {1, 3, 7, 15, 31};
        byte[] contributions = new byte[SinkingMedium.COUNT];
        contributions[SinkingMedium.RED_QUICKSAND.id()] = (byte) 191;
        contributions[SinkingMedium.ASSIMILATION_SLIME.id()] = (byte) 64;
        byte[] visualEntries = {
                (byte) SinkingMedium.RED_QUICKSAND.id(), (byte) 191,
                (byte) SinkingMedium.ASSIMILATION_SLIME.id(), (byte) 64
        };
        long[] visualSources = {12345L, 67890L};
        AssimilationStatePayload expected = new AssimilationStatePayload(
                19, AssimilationStage.RESTORING, 0.876F, 0.432F, 17,
                12.25D, 63.5D, -9.75D, 37.5F, -14.0F,
                7.5F, -4.25F, 8.25F, 0.15F,
                0x13572468, SinkingMedium.RED_QUICKSAND.id(),
                contributions, visualEntries, visualSources,
                revealed, 417, 2, AssimilationQteAction.HOLD,
                2, 4, 39, 3, 11,
                true, 0.31F, 0.54F, 0.47F, false, 27,
                6, (byte) 1, 6, 9, AssimilationProfile.DEFAULT);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        AssimilationStatePayload.STREAM_CODEC.encode(buffer, expected);
        AssimilationStatePayload actual = AssimilationStatePayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected.entityId(), actual.entityId());
        assertEquals(expected.stage(), actual.stage());
        assertEquals(expected.progress(), actual.progress(), 0.0011F);
        assertEquals(expected.shellIntegrity(), actual.shellIntegrity(), 0.0011F);
        assertEquals(expected.restoringTicks(), actual.restoringTicks());
        assertEquals(expected.anchorX(), actual.anchorX());
        assertEquals(expected.anchorY(), actual.anchorY());
        assertEquals(expected.anchorZ(), actual.anchorZ());
        assertEquals(expected.bodyPitch(), actual.bodyPitch());
        assertEquals(expected.bodyRoll(), actual.bodyRoll());
        assertEquals(expected.patternSeed(), actual.patternSeed());
        assertEquals(expected.mediumId(), actual.mediumId());
        assertArrayEquals(contributions, actual.contributions());
        assertArrayEquals(visualEntries, actual.visualPaletteEntries());
        assertArrayEquals(visualSources, actual.visualSources());
        assertEquals(expected.qteCell(), actual.qteCell());
        assertEquals(expected.qteButton(), actual.qteButton());
        assertEquals(expected.qteAction(), actual.qteAction());
        assertEquals(expected.qteRapidClicks(), actual.qteRapidClicks());
        assertEquals(expected.qteTraceProgress(), actual.qteTraceProgress());
        assertEquals(expected.qteTicksRemaining(), actual.qteTicksRemaining());
        assertEquals(expected.qteStreak(), actual.qteStreak());
        assertEquals(expected.qteSequence(), actual.qteSequence());
        assertEquals(expected.partialPurgeActive(), actual.partialPurgeActive());
        assertEquals(expected.partialPurgeZoneStart(), actual.partialPurgeZoneStart(), 0.0011F);
        assertEquals(expected.partialPurgeZoneEnd(), actual.partialPurgeZoneEnd(), 0.0011F);
        assertEquals(expected.partialPurgeCursor(), actual.partialPurgeCursor(), 0.0011F);
        assertEquals(expected.partialPurgeCursorForward(), actual.partialPurgeCursorForward());
        assertEquals(expected.partialPurgeCursorOneWayTicks(), actual.partialPurgeCursorOneWayTicks());
        assertEquals(expected.partialPurgeResult(), actual.partialPurgeResult());
        assertEquals(expected.partialPurgeRound(), actual.partialPurgeRound());
        assertEquals(expected.profile(), actual.profile());
        assertArrayEquals(revealed, actual.revealedCells());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void incrementalPacketMayOmitUnchangedProfile() {
        byte[] contributions = new byte[SinkingMedium.COUNT];
        contributions[SinkingMedium.ASSIMILATION_SLIME.id()] = (byte) 127;
        AssimilationStatePayload expected = new AssimilationStatePayload(
                7, AssimilationStage.ASSIMILATING, 0.5F, 0.0F, 0,
                0.0D, 0.0D, 0.0D, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 17,
                SinkingMedium.ASSIMILATION_SLIME.id(), contributions,
                new byte[] {(byte) SinkingMedium.ASSIMILATION_SLIME.id(), (byte) 255},
                new long[] {0L},
                new byte[0], -1, 0, AssimilationQteAction.NONE,
                0, 0, 0, 0, 0,
                false, 0.0F, 0.0F, 0.0F, true, 24,
                0, (byte) 0, 0, 0, null);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        AssimilationStatePayload.STREAM_CODEC.encode(buffer, expected);
        int encodedBytes = buffer.readableBytes();
        AssimilationStatePayload actual = AssimilationStatePayload.STREAM_CODEC.decode(buffer);

        assertEquals(null, actual.profile());
        assertArrayEquals(contributions, actual.contributions());
        assertTrue(encodedBytes < 100, "incremental assimilation state should stay compact");
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
