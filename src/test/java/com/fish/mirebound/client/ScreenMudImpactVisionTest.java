package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.network.payload.MudClodScreenImpactPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScreenMudImpactVisionTest {
    @AfterEach
    void resetImpacts() {
        ScreenMudImpactVision.reset();
    }

    @Test
    void impactUsesTheOrdinaryVisionGridAndFadesAway() {
        long startedAt = 100L;
        ScreenMudImpactVision.accept(
                new MudClodScreenImpactPayload(0.82F, 0x4D554443L),
                startedAt);

        int initialCells = visibleCells(startedAt);
        int expandedCells = visibleCells(startedAt + 6L);
        float heldPeak = peakCoverage(startedAt + 12L);
        float fadingPeak = peakCoverage(startedAt + 70L);

        assertTrue(initialCells > 0);
        assertTrue(expandedCells > initialCells);
        assertTrue(heldPeak > fadingPeak);
        assertTrue(ScreenMudImpactVision.active(startedAt + 12L));
        assertFalse(ScreenMudImpactVision.active(startedAt + 91L));
    }

    private static int visibleCells(long gameTime) {
        int count = 0;
        for (int band = 0; band < 48; band++) {
            for (int lane = 0; lane < 48; lane++) {
                if (ScreenMudImpactVision.coverageAt(
                        band, lane, gameTime) > 0.04F) {
                    count++;
                }
            }
        }
        return count;
    }

    private static float peakCoverage(long gameTime) {
        float peak = 0.0F;
        for (int band = 0; band < 48; band++) {
            for (int lane = 0; lane < 48; lane++) {
                peak = Math.max(peak,
                        ScreenMudImpactVision.coverageAt(
                                band, lane, gameTime));
            }
        }
        return peak;
    }
}
