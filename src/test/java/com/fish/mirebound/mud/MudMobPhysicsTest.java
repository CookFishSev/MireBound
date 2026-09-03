package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudMobPhysicsTest {
    @Test
    void entityDepthLimitWinsInADeepColumn() {
        assertEquals(9.2D, MudMobPhysics.targetFootY(10.0D, 8.0D, 2.0D, 0.4D), 1.0E-9D);
    }

    @Test
    void availableColumnDepthCapsTheTarget() {
        assertEquals(9.52D, MudMobPhysics.targetFootY(10.0D, 9.5D, 2.0D, 0.4D), 1.0E-9D);
    }

    @Test
    void veryThinColumnsKeepTheMinimumSupportDepth() {
        assertEquals(9.96D, MudMobPhysics.targetFootY(10.0D, 9.97D, 2.0D, 0.4D), 1.0E-9D);
    }

    @Test
    void compatibilityCoverageScansAreStaggeredAcrossEntities() {
        assertTrue(MudMobPhysics.coverageFallbackDue(8, 2));
        assertFalse(MudMobPhysics.coverageFallbackDue(8, 3));
    }

    @Test
    void oversizedCompatibilityBoundsSampleBothEndsAndTheMiddle() {
        assertEquals(-20, MudMobPhysics.sampledCoordinate(-20, 20, 0, 3));
        assertEquals(0, MudMobPhysics.sampledCoordinate(-20, 20, 1, 3));
        assertEquals(20, MudMobPhysics.sampledCoordinate(-20, 20, 2, 3));
    }
}
