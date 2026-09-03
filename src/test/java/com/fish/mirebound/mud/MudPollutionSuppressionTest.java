package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudPollutionSuppressionTest {
    @Test
    void externalCameraLeaseRemainsActiveThroughItsDeadline() {
        assertFalse(MudPhysics.suppressionLeaseExpired(100, 160));
        assertFalse(MudPhysics.suppressionLeaseExpired(160, 160));
    }

    @Test
    void externalCameraLeaseExpiresImmediatelyAfterItsDeadline() {
        assertTrue(MudPhysics.suppressionLeaseExpired(161, 160));
    }
}
