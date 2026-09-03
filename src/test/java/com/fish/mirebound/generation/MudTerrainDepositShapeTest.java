package com.fish.mirebound.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudTerrainDepositShapeTest {
    @Test
    void zeroRoughnessProducesTheConfiguredCircularLimit() {
        MudTerrainGenerationSettings settings = new MudTerrainGenerationSettings(
                8, 4, 0.0D, 6, 1234, false);

        assertTrue(MudTerrainDepositShape.contains(20, -10, 28, -10, settings));
        assertTrue(MudTerrainDepositShape.contains(20, -10, 20, -2, settings));
        assertFalse(MudTerrainDepositShape.contains(20, -10, 29, -10, settings));
        assertEquals(4, MudTerrainDepositShape.depth(20, -10, 20, -10, settings));
        assertEquals(0, MudTerrainDepositShape.depth(20, -10, 29, -10, settings));
    }

    @Test
    void roughDepositsAreDeterministicAndRemainConnectedAtTheirCenter() {
        MudTerrainGenerationSettings settings = new MudTerrainGenerationSettings(
                20, 6, 0.85D, 8, 771923, false);
        MudTerrainGenerationSettings sameSeed = new MudTerrainGenerationSettings(
                20, 6, 0.85D, 8, 771923, false);
        MudTerrainGenerationSettings otherSeed = new MudTerrainGenerationSettings(
                20, 6, 0.85D, 8, 771924, false);
        boolean seedChangedFootprint = false;

        for (int z = -20; z <= 20; z++) {
            for (int x = -20; x <= 20; x++) {
                assertEquals(
                        MudTerrainDepositShape.contains(0, 0, x, z, settings),
                        MudTerrainDepositShape.contains(0, 0, x, z, sameSeed));
                seedChangedFootprint |= MudTerrainDepositShape.contains(
                        0, 0, x, z, settings) != MudTerrainDepositShape.contains(
                                0, 0, x, z, otherSeed);
            }
        }
        assertTrue(seedChangedFootprint);
        assertTrue(MudTerrainDepositShape.contains(0, 0, 0, 0, settings));
        assertEquals(settings.thickness(),
                MudTerrainDepositShape.depth(0, 0, 0, 0, settings));
    }
}
