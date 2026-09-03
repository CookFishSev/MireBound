package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MudCoveragePatternSeedTest {
    @Test
    void playerSeedStaysStableUntilCoverageIsFullyCleared() {
        MudPlayerData data = new MudPlayerData();

        data.setSurfacePixelCoverage(
                MudBodyPart.BODY, MudSurface.FRONT, 4, 3,
                0.72F, SinkingMedium.MUD);
        int first = data.coveragePatternSeed;
        data.setSurfacePixelCoverage(
                MudBodyPart.LEFT_ARM, MudSurface.LEFT, 5, 1,
                0.61F, SinkingMedium.MIRE);

        assertNotEquals(0, first);
        assertEquals(first, data.coveragePatternSeed);

        data.clearSyncedParts();
        data.setSurfacePixelCoverage(
                MudBodyPart.BODY, MudSurface.FRONT, 4, 3,
                0.72F, SinkingMedium.MUD);

        assertNotEquals(0, data.coveragePatternSeed);
        assertNotEquals(first, data.coveragePatternSeed);
    }

    @Test
    void playerSeedSurvivesPersistentSaveAndLoad() {
        MudPlayerData original = new MudPlayerData();
        original.setSurfacePixelCoverage(
                MudBodyPart.HEAD, MudSurface.FRONT, 3, 4,
                0.84F, SinkingMedium.SOFT_QUICKSAND);

        MudPlayerData loaded = new MudPlayerData();
        loaded.loadPersistent(original.savePersistent());

        assertEquals(original.coveragePatternSeed, loaded.coveragePatternSeed);
    }
}
