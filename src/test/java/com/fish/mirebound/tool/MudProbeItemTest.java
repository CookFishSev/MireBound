package com.fish.mirebound.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudProbeItemTest {
    @Test
    void depthErrorIsSymmetricAroundTheMeasuredDepth() {
        double depth = 1.25D;
        double maximumError = 0.15D;

        for (long seed = 0L; seed < 1000L; seed++) {
            double reported = MudProbeItem.applyDepthError(depth, seed, maximumError);
            assertTrue(reported >= depth - maximumError - 1.0E-12D);
            assertTrue(reported <= depth + maximumError + 1.0E-12D);
        }
    }

    @Test
    void depthErrorCanBeDisabled() {
        assertEquals(1.25D, MudProbeItem.applyDepthError(1.25D, 42L, 0.0D));
    }
}
