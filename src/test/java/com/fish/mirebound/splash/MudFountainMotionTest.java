package com.fish.mirebound.splash;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudFountainMotionTest {
    @Test
    void delayedSpreadWaitsUntilTheJetApproachesItsApex() {
        assertFalse(MudFountainMotion.shouldBreakUp(true, 0.42D, 0.10D));
        assertFalse(MudFountainMotion.shouldBreakUp(true, 0.11D, 0.10D));
        assertTrue(MudFountainMotion.shouldBreakUp(true, 0.10D, 0.10D));
        assertTrue(MudFountainMotion.shouldBreakUp(true, -0.02D, 0.10D));
        assertFalse(MudFountainMotion.shouldBreakUp(false, -0.20D, 0.10D));
    }

    @Test
    void launchConeSeparatesOuterDropletsWithoutAnApexImpulse() {
        double core = MudFountainMotion.radialSpeed(0.60D, 0.80D, 1.0D, true, 0.5D);
        double outer = MudFountainMotion.radialSpeed(0.60D, 0.80D, 1.0D, false, 0.5D);
        assertTrue(core > 0.0D);
        assertTrue(outer > core);
        assertTrue(MudFountainMotion.upwardSpeed(0.60D, true, 0.5D) > 0.60D);
    }
}
