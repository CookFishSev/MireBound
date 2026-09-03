package com.fish.mirebound.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudTerrainGenerationSettingsTest {
    @Test
    void clampsPersistedClientValuesToServerLimits() {
        MudTerrainGenerationSettings settings = new MudTerrainGenerationSettings(
                200, -3, 4.0D, 90, -12, true);

        assertEquals(48, settings.radius());
        assertEquals(1, settings.thickness());
        assertEquals(1.0D, settings.edgeRoughness());
        assertEquals(24, settings.heightTolerance());
        assertEquals(0, settings.seed());
        assertTrue(settings.sameSourceOnly());
    }

    @Test
    void rejectsOutOfRangeWireValuesInsteadOfSilentlyTrustingThem() {
        assertTrue(MudTerrainGenerationSettings.validWireValues(
                12, 3, 0.55D, 6, 92821));
        assertFalse(MudTerrainGenerationSettings.validWireValues(
                49, 3, 0.55D, 6, 92821));
        assertFalse(MudTerrainGenerationSettings.validWireValues(
                12, 3, Double.NaN, 6, 92821));
        assertFalse(MudTerrainGenerationSettings.validWireValues(
                12, 3, 0.55D, 25, 92821));
    }

    @Test
    void lakeSurfaceHeightDefaultsToFourteenAndClampsToPixelBounds() {
        MudTerrainLakeSettings defaults = new MudTerrainLakeSettings(
                8, 4, 92821,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR);
        MudTerrainLakeSettings low = new MudTerrainLakeSettings(
                8, 4, 92821,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR,
                0, true);
        MudTerrainLakeSettings high = new MudTerrainLakeSettings(
                8, 4, 92821,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR,
                17, true);

        assertEquals(14, defaults.surfaceHeightPixels());
        assertEquals(1, low.surfaceHeightPixels());
        assertEquals(16, high.surfaceHeightPixels());
        assertTrue(MudTerrainLakeSettings.validWireValues(
                8, 4, 92821,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR, 14));
        assertFalse(MudTerrainLakeSettings.validWireValues(
                8, 4, 92821,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR, 17));
    }
}
