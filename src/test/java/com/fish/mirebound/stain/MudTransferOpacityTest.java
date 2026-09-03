package com.fish.mirebound.stain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudTransferOpacityTest {
    @Test
    void wallTransferUsesTheVisibleCoverageInsteadOfRawCoverage() {
        assertEquals(0.32F, MudWallStainSystem.wallTransferStrength(0.80F, 0.50F, 0.80F), 1.0E-6F);
        assertEquals(0.0F, MudWallStainSystem.wallTransferStrength(1.0F, 0.0F, 1.0F), 1.0E-6F);
    }

    @Test
    void repeatedWallTransfersMonotonicallyConsumeTheSource() {
        float coverage = 0.80F;
        for (int transfer = 0; transfer < 16; transfer++) {
            float next = MudWallStainSystem.drainTransferredCoverage(coverage, 0.10F, 0.35F);
            assertTrue(next <= coverage);
            if (coverage > 0.35F) {
                assertTrue(next < coverage);
            }
            coverage = next;
        }

        assertEquals(0.35F, coverage, 1.0E-6F);
        assertEquals(0.35F,
                MudWallStainSystem.drainTransferredCoverage(coverage, 0.10F, 0.35F),
                1.0E-6F);
    }

    @Test
    void quantizedArmorCoverageStopsAtItsRepresentableFloor() {
        float quantizedFloor = 89.0F / 255.0F;

        assertFalse(MudWallStainSystem.aboveTransferFloor(
                quantizedFloor, 0.35F, true));
        assertTrue(MudWallStainSystem.aboveTransferFloor(
                90.0F / 255.0F, 0.35F, true));
        assertTrue(MudWallStainSystem.aboveTransferFloor(
                Math.nextUp(0.35F), 0.35F, false));
    }

    @Test
    void repeatedContactDoesNotRefreshAnUnchangedWallStain() {
        assertFalse(MudFootprintBlockEntity.shouldRefreshWallLifetime(
                false, 100L, 90L));
        assertTrue(MudFootprintBlockEntity.shouldRefreshWallLifetime(
                true, 100L, 90L));
        assertFalse(MudFootprintBlockEntity.shouldRefreshWallLifetime(
                true, 200L, 90L));
    }
}
