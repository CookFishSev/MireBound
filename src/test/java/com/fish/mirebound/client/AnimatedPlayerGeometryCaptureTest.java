package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnimatedPlayerGeometryCaptureTest {
    @Test
    void shadowPassDoesNotClaimAnOtherwiseDueCapture() {
        int tick = 120;
        int previousCaptureTick = 118;

        assertFalse(AnimatedPlayerGeometryCapture.captureAttemptDue(
                tick, previousCaptureTick, true));
        assertTrue(AnimatedPlayerGeometryCapture.captureAttemptDue(
                tick, previousCaptureTick, false));
    }

    @Test
    void mainPassStillHonorsTheCaptureInterval() {
        assertFalse(AnimatedPlayerGeometryCapture.captureAttemptDue(
                120, 119, false));
        assertTrue(AnimatedPlayerGeometryCapture.captureAttemptDue(
                120, Integer.MIN_VALUE, false));
    }
}
