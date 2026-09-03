package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LivingSlimeSolverTest {
    private static final LivingSlimePhysicsProfile PROFILE =
            LivingSlimePhysicsProfile.DEFAULT;

    @Test
    void calmContactBeginsWithSlowNaturalCompression() {
        LivingSlimeSolver.Result result = solve(
                0.05D, 1.80D,
                0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D,
                false, false);

        assertTrue(result.motionY() < 0.0D);
        assertTrue(result.motionY() >= -0.002D);
        assertTrue(result.walkScale() > 0.99D);
    }

    @Test
    void displacedSurfaceMemoryPullsPlayerBackUp() {
        LivingSlimeSolver.Result result = solve(
                0.24D, 1.80D,
                0.0D, -0.02D, 0.0D,
                0.0D, 0.35D, 0.0D,
                false, false);

        assertTrue(result.motionY() > 0.0D);
        assertTrue(result.verticalTug() > 0.0D);
    }

    @Test
    void highSpeedImpactCompressesInsteadOfInstantlyRebounding() {
        LivingSlimeSolver.Result result = solve(
                0.12D, 1.80D,
                0.0D, -1.20D, 0.0D,
                0.0D, 0.0D, 0.0D,
                PROFILE.maxUpSpeed, false, false);

        assertTrue(result.motionY() < 0.0D);
        assertEquals(-PROFILE.maxDownSpeed, result.motionY(), 1.0E-9D);
        assertTrue(result.verticalRetention() < PROFILE.verticalRetention);
        assertEquals(PROFILE.maxUpSpeed, result.impactEnergy(), 1.0E-9D);
        assertFalse(result.impactReleased());
    }

    @Test
    void storedImpactReturnsOnlyWhenCompressedMemoryNaturallyReverses() {
        LivingSlimeSolver.Result result = solve(
                0.40D, 1.80D,
                0.0D, -0.01D, 0.0D,
                0.0D, 0.45D, 0.0D,
                0.36D, false, false);

        assertEquals(0.36D, result.motionY(), 1.0E-9D);
        assertEquals(0.0D, result.impactEnergy(), 1.0E-9D);
        assertTrue(result.impactReleased());
    }

    @Test
    void capturedImpactScalesWithFallSpeedUntilConfiguredCap() {
        double moderate = LivingSlimeSolver.captureImpact(PROFILE, -0.50D);
        double high = LivingSlimeSolver.captureImpact(PROFILE, -3.0D);

        assertEquals(0.50D * PROFILE.impactRestitution, moderate, 1.0E-9D);
        assertEquals(PROFILE.maxUpSpeed, high, 1.0E-9D);
        assertTrue(high > moderate);
        assertEquals(0.0D, LivingSlimeSolver.captureImpact(
                PROFILE, -PROFILE.impactThreshold), 1.0E-9D);
    }

    @Test
    void fullChargeUsesConfiguredImpulseAndLiftDuration() {
        double impulse = LivingSlimePhysics.struggleImpulse(
                PROFILE, 0.0D, 1.80D, 1.0D);

        assertEquals(PROFILE.struggleMax, impulse, 1.0E-9D);
        assertEquals(PROFILE.struggleLiftTicksMin,
                LivingSlimePhysics.struggleLiftTicks(PROFILE, 0.0D));
        assertEquals(PROFILE.struggleLiftTicksMax,
                LivingSlimePhysics.struggleLiftTicks(PROFILE, 1.0D));
    }

    @Test
    void horizontalMemoryActsOnAllDirections() {
        LivingSlimeSolver.Result result = solve(
                0.30D, 1.80D,
                0.0D, 0.0D, 0.0D,
                0.20D, 0.0D, -0.10D,
                false, false);

        assertTrue(result.motionX() > 0.0D);
        assertTrue(result.motionZ() < 0.0D);
    }

    @Test
    void columnLimitStopsDownwardMotionWithoutPassiveLift() {
        double limit = Math.max(
                PROFILE.minColumnDepth,
                1.80D - PROFILE.columnMargin);
        LivingSlimeSolver.Result result = solve(
                limit, 1.80D,
                0.0D, -0.20D, 0.0D,
                0.0D, 0.0D, 0.0D,
                false, false);

        assertEquals(0.0D, result.motionY(), 1.0E-9D);
    }

    @Test
    void struggleCarryIsNotPulledDownBySurfaceMemory() {
        LivingSlimeSolver.Result result = solve(
                0.55D, 1.80D,
                0.0D, 0.20D, 0.0D,
                0.0D, -0.80D, 0.0D,
                0.0D, false, true);

        assertEquals(0.20D, result.motionY(), 1.0E-9D);
        assertTrue(result.motionY() <= PROFILE.maxUpSpeed);
    }

    @Test
    void detachedMemoryExpiresOnlyAfterGraceWindow() {
        LivingSlimeRuntimeState state = new LivingSlimeRuntimeState();
        assertTrue(state.touch(1.0D, 2.0D, 3.0D));
        state.impactEnergy = 0.35D;
        state.detach();
        assertFalse(state.touch(1.0D, 2.0D, 3.0D));
        assertEquals(0.35D, state.impactEnergy, 1.0E-9D);
        for (int i = 0; i < 12; i++) {
            state.detach();
            assertTrue(state.anchorActive);
        }
        state.detach();
        assertFalse(state.anchorActive);
    }

    private static LivingSlimeSolver.Result solve(
            double depth,
            double availableDepth,
            double motionX,
            double motionY,
            double motionZ,
            double anchorDeltaX,
            double anchorDeltaY,
            double anchorDeltaZ,
            boolean crouching,
            boolean struggleCarry) {
        return solve(depth, availableDepth, motionX, motionY, motionZ,
                anchorDeltaX, anchorDeltaY, anchorDeltaZ, 0.0D,
                crouching, struggleCarry);
    }

    private static LivingSlimeSolver.Result solve(
            double depth,
            double availableDepth,
            double motionX,
            double motionY,
            double motionZ,
            double anchorDeltaX,
            double anchorDeltaY,
            double anchorDeltaZ,
            double impactEnergy,
            boolean crouching,
            boolean struggleCarry) {
        return LivingSlimeSolver.solve(
                PROFILE,
                new LivingSlimeSolver.Input(
                        depth,
                        availableDepth,
                        1.80D,
                        motionX,
                        motionY,
                        motionZ,
                        Math.sqrt(motionX * motionX + motionZ * motionZ),
                        anchorDeltaX,
                        anchorDeltaY,
                        anchorDeltaZ,
                        impactEnergy,
                        crouching,
                        struggleCarry));
    }
}
