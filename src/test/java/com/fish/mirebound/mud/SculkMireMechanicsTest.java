package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SculkMireMechanicsTest {
    private static final SculkMireProfile PROFILE = SculkMireProfile.DEFAULT;

    @Test
    void crouchingCanCrossTheSurfaceWithoutEnteringTheSinkState() {
        SculkMireRuntimeState state = new SculkMireRuntimeState();
        SculkMireMechanics.StepResult result = step(state, 0.01D, 0.8D,
                true, false, 0.34D, -0.02D, 0.12D);

        assertFalse(state.sunk());
        assertEquals(0.0D, result.motionY(), 1.0E-9D);
        assertEquals(0.34D * PROFILE.sneakWalkScale(), result.motionX(), 1.0E-9D);
    }

    @Test
    void actionAfterEntryAddsDownwardMotionAndThreat() {
        SculkMireRuntimeState state = new SculkMireRuntimeState();
        SculkMireMechanics.StepResult result = step(state, 0.30D, 1.0D,
                false, false, 0.0D, -0.002D, 0.0D);

        assertTrue(state.sunk());
        assertTrue(result.action());
        assertTrue(result.motionY() <= -PROFILE.actionSinkBoost() + 1.0E-9D);
        assertTrue(state.hiddenValue() > 0.0D);
    }

    @Test
    void stillCrouchWaitsThenRisesWithoutHorizontalDrift() {
        SculkMireRuntimeState state = new SculkMireRuntimeState();
        step(state, 0.30D, 1.0D, false, false, 0.0D, 0.0D, 0.0D);

        for (int tick = 1; tick < PROFILE.quietCrouchDelayTicks(); tick++) {
            SculkMireMechanics.StepResult waiting = step(state, 0.30D, 0.0D,
                    true, false, 0.2D, -0.01D, 0.2D);
            assertEquals(0.0D, waiting.motionX(), 1.0E-9D);
            assertEquals(0.0D, waiting.motionY(), 1.0E-9D);
            assertEquals(0.0D, waiting.motionZ(), 1.0E-9D);
        }

        SculkMireMechanics.StepResult rising = step(state, 0.30D, 0.0D,
                true, false, 0.2D, -0.01D, 0.2D);
        assertEquals(PROFILE.quietRiseSpeed(), rising.motionY(), 1.0E-9D);
    }

    @Test
    void finalRiseStepLatchesTheSafeSurfaceStateBeforeContactReachesZero() {
        SculkMireRuntimeState state = new SculkMireRuntimeState();
        state.escape().enter();
        for (int tick = 1; tick < PROFILE.quietCrouchDelayTicks(); tick++) {
            step(state, 0.010D, 0.0D, true, false, 0.0D, 0.0D, 0.0D);
        }

        SculkMireMechanics.StepResult finalRise = step(
                state, 0.010D, 0.0D, true, false, 0.0D, 0.0D, 0.0D);

        assertEquals(0.010D, finalRise.motionY(), 1.0E-9D);
        assertFalse(state.sunk());
        assertTrue(state.escape().escaped());
    }

    @Test
    void sculkSurfaceContactBridgesTheOrdinaryPenetrationThreshold() {
        assertTrue(MudContactRules.qualifiesSculkSurfaceContact(0.0D, true));
        assertTrue(MudContactRules.qualifiesSculkSurfaceContact(0.010D, true));
        assertFalse(MudContactRules.qualifiesSculkSurfaceContact(0.010D, false));
        assertFalse(MudContactRules.qualifiesSculkSurfaceContact(0.080D, true));
    }

    @Test
    void movingWhileCrouchedDoesNotCountAsQuietEscape() {
        SculkMireRuntimeState state = new SculkMireRuntimeState();
        step(state, 0.30D, 1.0D, false, false, 0.0D, 0.0D, 0.0D);
        for (int tick = 0; tick < 20; tick++) {
            step(state, 0.30D, 0.0D, true, false, 0.0D, 0.0D, 0.0D);
        }

        SculkMireMechanics.StepResult disturbed = step(state, 0.30D, 0.7D,
                true, false, 0.0D, 0.0D, 0.0D);
        assertTrue(disturbed.action());
        assertEquals(0, state.quietCrouchTicks());
        assertTrue(disturbed.motionY() < 0.0D);
    }

    @Test
    void movementAfterReachingTheSurfaceStaysSafeUntilCrouchIsReleased() {
        SculkMireRuntimeState state = new SculkMireRuntimeState();
        state.escape().enter();
        for (int tick = 0; tick < PROFILE.quietCrouchDelayTicks(); tick++) {
            step(state, 0.010D, 0.0D,
                    true, false, 0.0D, 0.0D, 0.0D);
        }
        assertFalse(state.sunk());
        assertTrue(state.escape().escaped());

        SculkMireMechanics.StepResult result = step(state,
                0.0D, 0.7D,
                true, false, 0.1D, 0.0D, 0.0D);
        assertFalse(state.sunk());
        assertFalse(result.action());
        assertEquals(0.1D * PROFILE.sneakWalkScale(), result.motionX(), 1.0E-9D);

        SculkMireMechanics.StepResult reentered = step(state,
                0.0D, 0.7D,
                false, false, 0.1D, 0.0D, 0.0D);
        assertTrue(state.sunk());
        assertTrue(reentered.action());
        assertTrue(reentered.motionY() < 0.0D);
    }

    @Test
    void threatStartsClampAndCalmEventuallyReleasesIt() {
        SculkMireRuntimeState state = new SculkMireRuntimeState();
        SculkMireMechanics.StepResult result = null;
        for (int tick = 0; tick < 200 && !state.clampActive(); tick++) {
            result = step(state, 0.35D, 1.0D, false, false,
                    0.0D, -0.01D, 0.0D);
        }

        assertTrue(state.clampActive());
        assertTrue(result.clampStarted());
        assertEquals(0.0D, result.walkScale(), 1.0E-9D);
        assertEquals(0.0D, state.hiddenValue(), 1.0E-9D);

        int initial = state.clampTicks();
        step(state, 0.35D, 1.0D, false, true, 0.0D, 0.0D, 0.0D);
        assertEquals(Math.min(PROFILE.clampMaximumTicks(),
                initial + PROFILE.clampExtensionTicks()), state.clampTicks());

        boolean released = false;
        for (int tick = 0; tick < PROFILE.clampMaximumTicks() + 2; tick++) {
            SculkMireMechanics.StepResult calm = step(state, 0.35D, 0.0D,
                    false, false, 0.0D, 0.0D, 0.0D);
            released |= calm.clampReleased();
        }
        assertTrue(released);
        assertFalse(state.clampActive());
    }

    private static SculkMireMechanics.StepResult step(SculkMireRuntimeState state,
            double depth, double movement, boolean crouching, boolean jumping,
            double motionX, double motionY, double motionZ) {
        return SculkMireMechanics.step(PROFILE, state, new SculkMireMechanics.Input(
                depth, 1.0D,
                motionX, motionY, motionZ,
                motionX * 0.5D, motionY, motionZ * 0.5D,
                0.5D,
                movement, 0.0D, jumping, crouching));
    }
}
