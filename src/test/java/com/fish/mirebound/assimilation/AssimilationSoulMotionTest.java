package com.fish.mirebound.assimilation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AssimilationSoulMotionTest {
    @Test
    void spaceAscendsAndShiftDescends() {
        assertEquals(1.0D, AssimilationSoulMotion.verticalInput(true, false), 0.0D);
        assertEquals(-1.0D, AssimilationSoulMotion.verticalInput(false, true), 0.0D);
        assertEquals(0.0D, AssimilationSoulMotion.verticalInput(true, true), 0.0D);
        assertEquals(0.0D, AssimilationSoulMotion.verticalInput(false, false), 0.0D);
    }

    @Test
    void forwardInputAlwaysStaysHorizontal() {
        Vec3 forward = AssimilationSoulMotion.inputDirection(37.0F, 1.0D, 0.0D, 0.0D);
        assertEquals(0.0D, forward.y, 0.0D);
        assertEquals(1.0D, forward.horizontalDistance(), 1.0E-6D);
    }

    @Test
    void onlyExplicitVerticalInputChangesAltitude() {
        Vec3 movement = AssimilationSoulMotion.inputDirection(90.0F, 1.0D, 0.0D, -1.0D);
        assertEquals(-1.0D, movement.x, 1.0E-6D);
        assertEquals(-1.0D, movement.y, 0.0D);
        assertEquals(0.0D, movement.z, 1.0E-6D);
    }

    @Test
    void emergenceMovesBehindFrozenFacingAndUsesSmoothEndpoints() {
        Vec3 eye = new Vec3(4.0D, 65.5D, -2.0D);
        Vec3 target = AssimilationSoulMotion.emergenceTarget(eye, 0.0F, 1.5D, 0.75D);
        assertEquals(4.0D, target.x, 1.0E-6D);
        assertEquals(66.25D, target.y, 1.0E-6D);
        assertEquals(-3.5D, target.z, 1.0E-6D);
        assertEquals(0.0D, AssimilationSoulMotion.smoothTransition(-1.0D), 0.0D);
        assertEquals(0.5D, AssimilationSoulMotion.smoothTransition(0.5D), 1.0E-9D);
        assertEquals(1.0D, AssimilationSoulMotion.smoothTransition(2.0D), 0.0D);
    }

    @Test
    void accelerationApproachesConfiguredSpeedWithoutInstantJump() {
        AssimilationProfile profile = AssimilationProfile.DEFAULT;
        Vec3 first = AssimilationSoulMotion.updateVelocity(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D),
                profile.soulMoveSpeed(), profile.soulAcceleration(), profile.soulDrag());
        assertTrue(first.z > 0.0D);
        assertTrue(first.z < profile.soulMoveSpeed());

        Vec3 velocity = first;
        for (int tick = 0; tick < 40; tick++) {
            velocity = AssimilationSoulMotion.updateVelocity(
                    velocity, new Vec3(0.0D, 0.0D, 1.0D),
                    profile.soulMoveSpeed(), profile.soulAcceleration(), profile.soulDrag());
        }
        assertEquals(profile.soulMoveSpeed(), velocity.z, 1.0E-6D);
    }

    @Test
    void releasedInputDeceleratesSmoothly() {
        AssimilationProfile profile = AssimilationProfile.DEFAULT;
        Vec3 start = new Vec3(profile.soulMoveSpeed(), 0.0D, 0.0D);
        Vec3 next = AssimilationSoulMotion.updateVelocity(
                start, Vec3.ZERO, profile.soulMoveSpeed(),
                profile.soulAcceleration(), profile.soulDrag());
        assertTrue(next.x > 0.0D);
        assertTrue(next.x < start.x);
    }

    @Test
    void observationBoundaryStopsOnlyTheBlockedVelocityAxis() {
        AssimilationSoulMotion.Step step = AssimilationSoulMotion.advance(
                new Vec3(1.9D, 0.0D, 0.0D),
                new Vec3(0.4D, 0.2D, -0.1D), Vec3.ZERO, 2.0D);
        assertEquals(2.0D, step.position().x, 0.0D);
        assertEquals(0.0D, step.velocity().x, 0.0D);
        assertEquals(0.2D, step.velocity().y, 0.0D);
        assertEquals(-0.1D, step.velocity().z, 0.0D);
    }

    @Test
    void observationBoundarySoftensOutwardMotionBeforeTheSafetyClamp() {
        AssimilationSoulMotion.Step step = AssimilationSoulMotion.advance(
                new Vec3(1.75D, 0.0D, 0.0D),
                new Vec3(0.20D, 0.0D, 0.0D), Vec3.ZERO, 2.0D, 0.25D);
        assertTrue(step.velocity().x > 0.0D);
        assertTrue(step.velocity().x < 0.20D);
        assertTrue(step.position().x < 1.95D);
    }

    @Test
    void distancePresentationCurvesAreBoundedAndMonotonic() {
        assertEquals(0.5D, AssimilationSoulMotion.distanceFraction(
                new Vec3(1.0D, -0.25D, 0.5D), Vec3.ZERO, 2.0D), 1.0E-9D);
        double near = AssimilationSoulMotion.distanceEffect(0.25D, 0.18D);
        double far = AssimilationSoulMotion.distanceEffect(0.80D, 0.18D);
        assertTrue(near > 0.0D);
        assertTrue(far > near);
        assertEquals(1.0D, AssimilationSoulMotion.distanceEffect(2.0D, 0.18D), 0.0D);
    }

    @Test
    void restoreBlackoutCoversReturnAndReleasesAfterTheStageHandoff() {
        assertEquals(0.0F, AssimilationSoulMotion.restoreBlackout(32, 32, 6), 0.0F);
        assertEquals(1.0F, AssimilationSoulMotion.restoreBlackout(26, 32, 6), 0.0F);
        assertEquals(1.0F, AssimilationSoulMotion.restoreBlackout(0, 32, 6), 0.0F);
        assertTrue(AssimilationSoulMotion.restoreBlackout(30, 32, 6)
                < AssimilationSoulMotion.restoreBlackout(28, 32, 6));
        assertEquals(1.0F, AssimilationSoulMotion.restoreReleaseBlackout(6, 6), 0.0F);
        assertEquals(0.0F, AssimilationSoulMotion.restoreReleaseBlackout(0, 6), 0.0F);
        assertEquals(0.0D, AssimilationSoulMotion.restoreReturnProgress(0.18D), 0.0D);
        assertEquals(1.0D, AssimilationSoulMotion.restoreReturnProgress(0.56D), 0.0D);
    }
}
