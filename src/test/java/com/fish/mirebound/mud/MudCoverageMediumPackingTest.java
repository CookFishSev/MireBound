package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.network.payload.MudCoverageSyncPayload;
import org.junit.jupiter.api.Test;

class MudCoverageMediumPackingTest {
    @Test
    void coveragePackingPreservesEcologyMediumIdsAboveFifteen() {
        byte[] surfaceCoverage = new byte[MudSurfaceLayout.CELL_COUNT];
        byte[] surfaceMedium = new byte[MudSurfaceLayout.CELL_COUNT];
        int surfaceIndex = MudSurfaceLayout.cellIndex(MudBodyPart.HEAD, MudSurface.FRONT, 0, 0);
        surfaceCoverage[surfaceIndex] = (byte) 255;
        surfaceMedium[surfaceIndex] = (byte) SinkingMedium.MIRE.id();

        byte[] visionCoverage = new byte[MudBodyPart.VISION_COUNT];
        byte[] visionMedium = new byte[MudBodyPart.VISION_COUNT];
        visionCoverage[0] = (byte) 255;
        visionMedium[0] = (byte) SinkingMedium.INSECT_MOUND.id();
        byte[] capeCoverage = new byte[MudCapeLayout.CELL_COUNT];
        byte[] capeMedium = new byte[MudCapeLayout.CELL_COUNT];
        capeCoverage[MudCapeLayout.index(7, 4)] = (byte) 255;
        capeMedium[MudCapeLayout.index(7, 4)] = (byte) SinkingMedium.TAR.id();
        capeCoverage[MudCapeLayout.index(MudCapeLayout.Side.INNER, 7, 4)] = (byte) 255;
        capeMedium[MudCapeLayout.index(MudCapeLayout.Side.INNER, 7, 4)] =
                (byte) SinkingMedium.LIVING_SLIME.id();

        MudCoverageSyncPayload payload = new MudCoverageSyncPayload(
                1,
                0x41A55A17,
                1000,
                SinkingMedium.MIRE.id(),
                1000,
                visionCoverage,
                MudCoverageSyncPayload.packVisionMedium(visionMedium),
                surfaceCoverage,
                MudCoverageSyncPayload.packSurfaceMedium(surfaceMedium),
                capeCoverage,
                MudCoverageSyncPayload.packCapeMedium(capeMedium));

        assertEquals(0x41A55A17, payload.coveragePatternSeed());
        assertEquals(SinkingMedium.MIRE,
                payload.surfacePixelMedium(MudBodyPart.HEAD, MudSurface.FRONT, 0, 0));
        assertEquals(SinkingMedium.INSECT_MOUND, payload.visionMedium(0, 0));
        assertEquals(SinkingMedium.TAR,
                payload.capePixelMedium(MudCapeLayout.Side.OUTER, 7, 4));
        assertEquals(SinkingMedium.LIVING_SLIME,
                payload.capePixelMedium(MudCapeLayout.Side.INNER, 7, 4));
    }
}
