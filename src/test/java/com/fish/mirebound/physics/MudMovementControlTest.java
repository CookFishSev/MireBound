package com.fish.mirebound.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MudMovementControlTest {
    @Test
    void fovCorrectionRemovesOnlyTheMudSlowdown() {
        assertEquals(
                4.0D / 3.0D,
                MudMovementControl.fovCorrection(0.05D, 0.10D, 0.10D),
                1.0E-9D);
    }

    @Test
    void invalidWalkingSpeedKeepsVanillaFov() {
        assertEquals(
                1.0D,
                MudMovementControl.fovCorrection(0.05D, 0.10D, 0.0D),
                1.0E-9D);
    }

    @Test
    void walkScaleModifierUsesTheExistingSafetyBounds() {
        assertEquals(-1.0D, MudMovementControl.movementModifierAmount(-4.0D), 1.0E-9D);
        assertEquals(-0.25D, MudMovementControl.movementModifierAmount(0.75D), 1.0E-9D);
        assertEquals(0.20D, MudMovementControl.movementModifierAmount(2.0D), 1.0E-9D);
    }

    @Test
    void stepHeightModifierTargetsTheConfiguredFinalHeight() {
        assertEquals(
                -2.0D / 3.0D,
                MudMovementControl.stepHeightModifierAmount(0.60D, 0.20D),
                1.0E-9D);
        assertEquals(
                -1.0D,
                MudMovementControl.stepHeightModifierAmount(0.60D, 0.0D),
                1.0E-9D);
        assertEquals(
                0.0D,
                MudMovementControl.stepHeightModifierAmount(0.0D, 0.20D),
                1.0E-9D);
    }
}
