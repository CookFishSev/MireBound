package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCapeLayout;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MudCoverageDeltaPayloadTest {
    @Test
    void codecPreservesSparseSurfaceCapeAndVisionChanges() {
        MudCoverageDeltaPayload expected = new MudCoverageDeltaPayload(
                17, 0x41A55A17, 643, SinkingMedium.TAR.id(), 271,
                new int[] {0, 53, MudSurfaceLayout.CELL_COUNT - 1},
                new byte[] {1, 71, (byte) 255},
                new byte[] {(byte) SinkingMedium.MUD.id(), (byte) SinkingMedium.TAR.id(),
                        (byte) SinkingMedium.SOFT_QUICKSAND.id()},
                new int[] {3, 0x4A7F21, 0x7FFFFFFF},
                new int[] {2, MudCapeLayout.CELL_COUNT - 1},
                new byte[] {42, (byte) 213},
                new byte[] {(byte) SinkingMedium.PEAT_BOG.id(),
                        (byte) SinkingMedium.LIVING_SLIME.id()},
                new int[] {7, 9001},
                new int[] {1, MudBodyPart.VISION_COUNT - 1},
                new byte[] {9, (byte) 190},
                new byte[] {(byte) SinkingMedium.SILT.id(),
                        (byte) SinkingMedium.PEAT_BOG.id()});
        RegistryFriendlyByteBuf buffer = buffer();

        MudCoverageDeltaPayload.STREAM_CODEC.encode(buffer, expected);
        MudCoverageDeltaPayload actual = MudCoverageDeltaPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected.entityId(), actual.entityId());
        assertEquals(expected.coveragePatternSeed(), actual.coveragePatternSeed());
        assertEquals(expected.coveragePermille(), actual.coveragePermille());
        assertEquals(expected.mediumId(), actual.mediumId());
        assertEquals(expected.visionPermille(), actual.visionPermille());
        assertArrayEquals(expected.surfaceIndices(), actual.surfaceIndices());
        assertArrayEquals(expected.surfaceCoverage(), actual.surfaceCoverage());
        assertArrayEquals(expected.surfaceMedium(), actual.surfaceMedium());
        assertArrayEquals(expected.surfaceAppearance(), actual.surfaceAppearance());
        assertArrayEquals(expected.capeIndices(), actual.capeIndices());
        assertArrayEquals(expected.capeCoverage(), actual.capeCoverage());
        assertArrayEquals(expected.capeMedium(), actual.capeMedium());
        assertArrayEquals(expected.capeAppearance(), actual.capeAppearance());
        assertArrayEquals(expected.visionIndices(), actual.visionIndices());
        assertArrayEquals(expected.visionCoverage(), actual.visionCoverage());
        assertArrayEquals(expected.visionMedium(), actual.visionMedium());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void encoderRejectsUnsortedIndices() {
        MudCoverageDeltaPayload invalid = new MudCoverageDeltaPayload(
                1, 0, SinkingMedium.MUD.id(), 0,
                new int[] {8, 7}, new byte[] {1, 1}, new byte[] {0, 0}, new int[] {0, 0},
                new int[0], new byte[0], new byte[0], new int[0],
                new int[0], new byte[0], new byte[0]);
        RegistryFriendlyByteBuf buffer = buffer();

        assertThrows(IllegalArgumentException.class,
                () -> MudCoverageDeltaPayload.STREAM_CODEC.encode(buffer, invalid));
        buffer.release();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
