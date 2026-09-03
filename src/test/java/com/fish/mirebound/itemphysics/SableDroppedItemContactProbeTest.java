package com.fish.mirebound.itemphysics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableDroppedItemContactProbeTest {
    @Test
    void traceSamplingIsDenseEnoughForThinMudAndStrictlyBounded() {
        assertEquals(1, SableDroppedItemContactProbe.traceSegments(0.0D));
        assertEquals(3, SableDroppedItemContactProbe.traceSegments(0.125D));
        assertEquals(48, SableDroppedItemContactProbe.traceSegments(128.0D));
        assertTrue(SableDroppedItemContactProbe.traceSegments(1.0D) <= 48);
    }
}
