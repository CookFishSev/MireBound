package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

class MudPlayerDataCrackCleaningTest {
    @Test
    void crackCleaningOnlyClearsSelectedCanonicalCells() {
        MudPlayerData data = new MudPlayerData();
        int cleared = MudSurfaceLayout.cellIndex(
                MudBodyPart.BODY, MudSurface.FRONT, 5, 3);
        int retained = MudSurfaceLayout.cellIndex(
                MudBodyPart.BODY, MudSurface.FRONT, 5, 4);
        data.surfaceCoverage[cleared] = 0.8F;
        data.surfaceCoverage[retained] = 0.7F;
        data.surfaceMedium[cleared] = (byte) SinkingMedium.TAR.id();
        data.surfaceMedium[retained] = (byte) SinkingMedium.MIRE.id();
        data.refreshCoverageAfterSurfaceUpdate();

        BitSet cells = new BitSet(MudSurfaceLayout.CELL_COUNT);
        cells.set(cleared);

        assertTrue(data.clearSurfaceCoverage(cells));
        assertEquals(0.0F, data.surfaceCoverage[cleared]);
        assertEquals(SinkingMedium.MUD.id(), data.surfaceMedium[cleared] & 0xFF);
        assertEquals(0.7F, data.surfaceCoverage[retained]);
        assertTrue(data.coveragePersistenceDirty);
        assertEquals(0.7F, data.coverage, 1.0E-7F);
        assertFalse(data.clearSurfaceCoverage(cells));
    }
}
