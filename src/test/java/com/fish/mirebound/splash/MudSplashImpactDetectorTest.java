package com.fish.mirebound.splash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudSplashImpactDetectorTest {
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    @Test
    void authoritativeContactRequiresRealPenetration() {
        assertFalse(MudSplashImpactDetector.hasQualifiedContact(frame(0.0119D)));
        assertTrue(MudSplashImpactDetector.hasQualifiedContact(frame(0.012D)));
        assertTrue(frame(0.05D).createSurfacePile());
    }

    @Test
    void outsideToRealContactTriggersOnlyOnce() {
        assertTrue(MudSplashImpactDetector.qualifiesEntry(
                false, true, 0.44D, 0.30D));
        assertFalse(MudSplashImpactDetector.qualifiesEntry(
                true, true, 0.44D, 0.30D));
        assertFalse(MudSplashImpactDetector.qualifiesEntry(
                false, false, 0.44D, 0.30D));
    }

    @Test
    void weakAndTangentialEntriesDoNotTrigger() {
        assertFalse(MudSplashImpactDetector.qualifiesEntry(
                false, true, 0.12D, 0.30D));

        Vec3 selected = MudSplashImpactDetector.selectImpactVelocity(
                new Vec3(0.42D, -0.01D, 0.0D),
                new Vec3(0.42D, -0.01D, 0.0D),
                new Vec3(0.42D, 0.0D, 0.0D),
                UP,
                0.0D);
        assertFalse(MudSplashImpactDetector.qualifiesEntry(
                false, true,
                MudSplashImpactDetector.inwardSpeed(selected, UP),
                0.30D));
    }

    @Test
    void observedDisplacementSurvivesContactVelocitySlowdown() {
        Vec3 selected = MudSplashImpactDetector.selectImpactVelocity(
                new Vec3(0.0D, -0.58D, 0.0D),
                new Vec3(0.0D, -0.51D, 0.0D),
                new Vec3(0.0D, -0.04D, 0.0D),
                UP,
                0.0D);

        assertEquals(0.58D,
                MudSplashImpactDetector.inwardSpeed(selected, UP), 1.0E-9D);
    }

    @Test
    void fallDistanceRecoversImpactAfterVelocityWasCleared() {
        Vec3 selected = MudSplashImpactDetector.selectImpactVelocity(
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, UP, 4.0D);

        assertEquals(0.80D,
                MudSplashImpactDetector.inwardSpeed(selected, UP), 1.0E-9D);
        assertTrue(MudSplashImpactDetector.qualifiesEntry(
                false, true,
                MudSplashImpactDetector.inwardSpeed(selected, UP),
                0.30D));
    }

    @Test
    void fallDistanceDoesNotInventSidewaysImpact() {
        Vec3 sideNormal = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 selected = MudSplashImpactDetector.selectImpactVelocity(
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, sideNormal, 20.0D);

        assertEquals(0.0D,
                MudSplashImpactDetector.inwardSpeed(selected, sideNormal), 1.0E-9D);
    }

    @Test
    void teleportCorrectionIsRejectedButFastObservedMotionIsKept() {
        Vec3 displacement = new Vec3(5.0D, -2.0D, 0.0D);

        assertTrue(MudSplashImpactDetector.isCorrectionLike(
                displacement, Vec3.ZERO, Vec3.ZERO));
        assertFalse(MudSplashImpactDetector.isCorrectionLike(
                displacement,
                new Vec3(5.0D, -2.0D, 0.0D),
                new Vec3(5.0D, -2.0D, 0.0D)));
        assertTrue(MudSplashImpactDetector.isCorrectionLike(
                new Vec3(33.0D, 0.0D, 0.0D),
                new Vec3(33.0D, 0.0D, 0.0D),
                new Vec3(33.0D, 0.0D, 0.0D)));
    }

    @Test
    void selectedInwardSpeedGrowsMonotonically() {
        Vec3 slow = MudSplashImpactDetector.selectImpactVelocity(
                new Vec3(0.0D, -0.36D, 0.0D),
                new Vec3(0.0D, -0.34D, 0.0D),
                Vec3.ZERO,
                UP,
                0.0D);
        Vec3 fast = MudSplashImpactDetector.selectImpactVelocity(
                new Vec3(0.0D, -1.10D, 0.0D),
                new Vec3(0.0D, -0.96D, 0.0D),
                Vec3.ZERO,
                UP,
                0.0D);

        assertTrue(MudSplashImpactDetector.inwardSpeed(fast, UP)
                > MudSplashImpactDetector.inwardSpeed(slow, UP));
    }

    private static MudSplashImpactDetector.ContactFrame frame(double depth) {
        return new MudSplashImpactDetector.ContactFrame(
                SinkingMedium.MUD,
                new Vec3(0.5D, 1.0D, 0.5D),
                UP,
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                depth,
                1.0D,
                true);
    }
}
