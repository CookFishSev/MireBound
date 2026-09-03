package com.fish.mirebound.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RagdollJointLimitsTest {
    private static final double INWARD = Math.toRadians(10.0D);
    private static final double OUTWARD = Math.toRadians(100.0D);
    private static final double SWING = Math.toRadians(75.0D);

    @Test
    void frameIsOrthonormalAndRightHanded() {
        RagdollJointLimits.Frame frame = RagdollJointLimits.Frame.of(
                new Vec3(1.0D, 0.40D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D));

        assertEquals(1.0D, frame.lateral().length(), 1.0E-9D);
        assertEquals(1.0D, frame.up().length(), 1.0E-9D);
        assertEquals(1.0D, frame.forward().length(), 1.0E-9D);
        assertEquals(0.0D, frame.lateral().dot(frame.up()), 1.0E-9D);
        assertEquals(0.0D, frame.up().dot(frame.forward()), 1.0E-9D);
        assertEquals(0.0D, frame.forward().dot(frame.lateral()), 1.0E-9D);
        Vec3 expectedForward = frame.lateral().cross(frame.up());
        assertEquals(0.0D, expectedForward.subtract(frame.forward()).length(), 1.0E-9D);
    }

    @Test
    void degenerateFrameHintsFallBackInsteadOfProducingNaN() {
        RagdollJointLimits.Frame collinear = RagdollJointLimits.Frame.of(
                new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D));
        RagdollJointLimits.Frame empty = RagdollJointLimits.Frame.of(Vec3.ZERO, Vec3.ZERO);

        for (RagdollJointLimits.Frame frame : new RagdollJointLimits.Frame[] {collinear, empty}) {
            assertEquals(1.0D, frame.lateral().length(), 1.0E-9D);
            assertEquals(1.0D, frame.up().length(), 1.0E-9D);
            assertEquals(1.0D, frame.forward().length(), 1.0E-9D);
            assertEquals(0.0D, frame.lateral().dot(frame.up()), 1.0E-9D);
        }
    }

    @Test
    void frameRoundTripsLocalAndWorldVectors() {
        RagdollJointLimits.Frame frame = RagdollJointLimits.Frame.of(
                new Vec3(0.30D, 0.10D, -0.90D), new Vec3(0.10D, 0.95D, 0.20D));
        Vec3 local = new Vec3(0.35D, -0.80D, 0.42D);

        Vec3 roundTripped = frame.toLocal(frame.toWorld(local));

        assertEquals(local.x, roundTripped.x, 1.0E-9D);
        assertEquals(local.y, roundTripped.y, 1.0E-9D);
        assertEquals(local.z, roundTripped.z, 1.0E-9D);
    }

    @Test
    void rootBoneClampsSwingAndMirrorsInwardTravel() {
        RagdollJointLimits.Limited forward = limit(direction(0.0D, 120.0D), true);
        RagdollJointLimits.Limited backward = limit(direction(0.0D, -120.0D), false);
        RagdollJointLimits.Limited leftInward = limit(direction(-80.0D, 0.0D), true);
        RagdollJointLimits.Limited rightInward = limit(direction(80.0D, 0.0D), false);

        assertEquals(75.0D, Math.toDegrees(forward.swingAngle()), 1.0E-6D);
        assertEquals(-75.0D, Math.toDegrees(backward.swingAngle()), 1.0E-6D);
        assertEquals(-10.0D, Math.toDegrees(leftInward.lateralAngle()), 1.0E-6D);
        assertEquals(10.0D, Math.toDegrees(rightInward.lateralAngle()), 1.0E-6D);
        assertEquals(1.0D, forward.direction().length(), 1.0E-8D);
        assertEquals(1.0D, backward.direction().length(), 1.0E-8D);
    }

    @Test
    void rootBoneClampsMirroredOutwardTravel() {
        RagdollJointLimits.Limited left = limit(direction(95.0D, 0.0D), true);
        RagdollJointLimits.Limited right = limit(direction(-95.0D, 0.0D), false);
        RagdollJointLimits.Limited leftPastLimit = limit(direction(130.0D, 0.0D), true);
        RagdollJointLimits.Limited rightPastLimit = limit(direction(-130.0D, 0.0D), false);

        assertEquals(95.0D, Math.toDegrees(left.lateralAngle()), 1.0E-6D);
        assertEquals(-95.0D, Math.toDegrees(right.lateralAngle()), 1.0E-6D);
        assertEquals(100.0D, Math.toDegrees(leftPastLimit.lateralAngle()), 1.0E-6D);
        assertEquals(-100.0D, Math.toDegrees(rightPastLimit.lateralAngle()), 1.0E-6D);
    }

    @Test
    void inwardLimitCannotBeBypassedByAnOutwardPreviousPose() {
        // The alternate angle branch describes the same direction as "still outward, plus a large
        // forward kick". Picking it on continuity alone would let the leg cross the midline freely.
        RagdollJointLimits.Limited left = RagdollJointLimits.limitRootBone(
                direction(-100.0D, 0.0D), RagdollJointLimits.Frame.IDENTITY, true,
                INWARD, OUTWARD, SWING, 0.0D, Math.toRadians(95.0D));
        RagdollJointLimits.Limited right = RagdollJointLimits.limitRootBone(
                direction(100.0D, 0.0D), RagdollJointLimits.Frame.IDENTITY, false,
                INWARD, OUTWARD, SWING, 0.0D, Math.toRadians(-95.0D));

        assertEquals(-10.0D, Math.toDegrees(left.lateralAngle()), 1.0E-6D);
        assertEquals(10.0D, Math.toDegrees(right.lateralAngle()), 1.0E-6D);
        assertEquals(0.0D, Math.toDegrees(left.swingAngle()), 1.0E-6D);
        assertEquals(0.0D, Math.toDegrees(right.swingAngle()), 1.0E-6D);
    }

    @Test
    void rootBoneLeavesALegalDirectionAlone() {
        Vec3 requested = direction(40.0D, 25.0D);

        RagdollJointLimits.Limited limited = limit(requested, true);

        assertEquals(0.0D, limited.direction().subtract(requested).length(), 1.0E-9D);
        assertEquals(40.0D, Math.toDegrees(limited.lateralAngle()), 1.0E-6D);
        assertEquals(25.0D, Math.toDegrees(limited.swingAngle()), 1.0E-6D);
    }

    @Test
    void rootBoneLimitsFollowARotatedBodyFrame() {
        // Lying face-down: the body's up axis points along world -Z and its forward along world +Y.
        RagdollJointLimits.Frame frame = RagdollJointLimits.Frame.of(
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, -1.0D));
        Vec3 pastOutward = frame.toWorld(direction(130.0D, 0.0D));

        RagdollJointLimits.Limited limited = RagdollJointLimits.limitRootBone(
                pastOutward, frame, true, INWARD, OUTWARD, SWING, 0.0D, 0.0D);

        assertEquals(100.0D, Math.toDegrees(limited.lateralAngle()), 1.0E-6D);
        Vec3 expected = frame.toWorld(direction(100.0D, 0.0D));
        assertEquals(0.0D, limited.direction().subtract(expected).length(), 1.0E-8D);
    }

    @Test
    void zeroLengthRootBoneKeepsThePreviousAngles() {
        RagdollJointLimits.Limited limited = RagdollJointLimits.limitRootBone(
                Vec3.ZERO, RagdollJointLimits.Frame.IDENTITY, true,
                INWARD, OUTWARD, SWING, 0.25D, -0.40D);

        assertEquals(0.25D, limited.swingAngle(), 1.0E-12D);
        assertEquals(-0.40D, limited.lateralAngle(), 1.0E-12D);
        assertEquals(Vec3.ZERO, limited.direction());
    }

    @Test
    void hingeLeavesALegalBendUntouchedIncludingOutOfPlanePlay() {
        Vec3 root = Vec3.ZERO;
        Vec3 joint = new Vec3(0.0D, -1.0D, 0.0D);
        // Roughly 60 degrees of flexion toward +Z, with a sideways component the hinge should not
        // straighten away: snapping every tick would read as a mannequin, not a body.
        Vec3 end = joint.add(new Vec3(0.20D, -0.50D, 0.84D).normalize());

        Vec3 limited = RagdollJointLimits.limitHinge(root, joint, end,
                RagdollJointLimits.ELBOW_MINIMUM_FLEXION,
                RagdollJointLimits.ELBOW_MAXIMUM_FLEXION,
                new Vec3(0.0D, 0.0D, 1.0D));

        assertSame(end, limited);
    }

    @Test
    void hingeRejectsHyperextensionByFlippingToTheAllowedSide() {
        Vec3 root = Vec3.ZERO;
        Vec3 joint = new Vec3(0.0D, -1.0D, 0.0D);
        Vec3 end = joint.add(new Vec3(0.0D, -1.0D, -1.0D).normalize());

        Vec3 limited = RagdollJointLimits.limitHinge(root, joint, end,
                RagdollJointLimits.ELBOW_MINIMUM_FLEXION,
                RagdollJointLimits.ELBOW_MAXIMUM_FLEXION,
                new Vec3(0.0D, 0.0D, 1.0D));

        Vec3 lower = limited.subtract(joint);
        assertTrue(lower.z > 0.0D, () -> "hyperextension survived: " + limited);
        assertEquals(1.0D, lower.length(), 1.0E-6D);
        // The fold magnitude is preserved; only its side changes.
        double flexion = Math.acos(new Vec3(0.0D, -1.0D, 0.0D).dot(lower.normalize()));
        assertEquals(45.0D, Math.toDegrees(flexion), 1.0E-4D);
    }

    @Test
    void hingeClampsAnOverFoldedJoint() {
        Vec3 root = Vec3.ZERO;
        Vec3 joint = new Vec3(0.0D, -1.0D, 0.0D);
        double folded = Math.toRadians(170.0D);
        Vec3 end = joint.add(new Vec3(0.0D,
                -Math.cos(folded), Math.sin(folded)));

        Vec3 limited = RagdollJointLimits.limitHinge(root, joint, end,
                RagdollJointLimits.KNEE_MINIMUM_FLEXION,
                RagdollJointLimits.KNEE_MAXIMUM_FLEXION,
                new Vec3(0.0D, 0.0D, 1.0D));

        Vec3 lower = limited.subtract(joint).normalize();
        double flexion = Math.acos(new Vec3(0.0D, -1.0D, 0.0D).dot(lower));
        assertEquals(155.0D, Math.toDegrees(flexion), 1.0E-4D);
        assertEquals(1.0D, limited.subtract(joint).length(), 1.0E-6D);
    }

    @Test
    void straightHingeGainsADefinedBendSide() {
        Vec3 root = Vec3.ZERO;
        Vec3 joint = new Vec3(0.0D, -1.0D, 0.0D);
        Vec3 end = new Vec3(0.0D, -2.0D, 0.0D);

        Vec3 limited = RagdollJointLimits.limitHinge(root, joint, end,
                RagdollJointLimits.KNEE_MINIMUM_FLEXION,
                RagdollJointLimits.KNEE_MAXIMUM_FLEXION,
                new Vec3(0.0D, 0.0D, -1.0D));

        Vec3 lower = limited.subtract(joint);
        assertTrue(lower.z < 0.0D, () -> "knee has no defined bend side: " + limited);
        assertEquals(1.0D, lower.length(), 1.0E-9D);
    }

    @Test
    void hingeKeepsItsPlaneWhenTheWholeLimbSwings() {
        // Upper bone rotated 90 degrees so it points along +X. A stored world-space hinge axis
        // would now be parallel to the bone and useless; deriving it per call keeps it valid.
        Vec3 root = Vec3.ZERO;
        Vec3 joint = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 end = joint.add(new Vec3(1.0D, 0.0D, -1.0D).normalize());

        Vec3 limited = RagdollJointLimits.limitHinge(root, joint, end,
                RagdollJointLimits.ELBOW_MINIMUM_FLEXION,
                RagdollJointLimits.ELBOW_MAXIMUM_FLEXION,
                new Vec3(0.0D, 0.0D, 1.0D));

        Vec3 lower = limited.subtract(joint);
        assertTrue(lower.z > 0.0D, () -> "swung hinge lost its plane: " + limited);
        assertEquals(1.0D, lower.length(), 1.0E-6D);
    }

    @Test
    void degenerateHingeInputsAreReturnedUnchanged() {
        Vec3 joint = new Vec3(0.0D, -1.0D, 0.0D);

        assertSame(joint, RagdollJointLimits.limitHinge(joint, joint, joint,
                RagdollJointLimits.ELBOW_MINIMUM_FLEXION,
                RagdollJointLimits.ELBOW_MAXIMUM_FLEXION,
                new Vec3(0.0D, 0.0D, 1.0D)));
    }

    @Test
    void neckClampsPitchAndRollButKeepsALegalTilt() {
        RagdollJointLimits.Frame frame = RagdollJointLimits.Frame.IDENTITY;
        Vec3 legal = neckDirection(20.0D, 15.0D);

        Vec3 untouched = RagdollJointLimits.limitNeck(legal, frame);
        Vec3 pitched = RagdollJointLimits.limitNeck(neckDirection(0.0D, 100.0D), frame);
        Vec3 rolled = RagdollJointLimits.limitNeck(neckDirection(80.0D, 0.0D), frame);

        assertSame(legal, untouched);
        assertEquals(70.0D, Math.toDegrees(Math.asin(pitched.normalize().z)), 1.0E-6D);
        Vec3 rolledUnit = rolled.normalize();
        assertEquals(45.0D, Math.toDegrees(Math.atan2(rolledUnit.x, rolledUnit.y)), 1.0E-6D);
    }

    @Test
    void neckPreservesTheHeadBoneLength() {
        Vec3 requested = neckDirection(80.0D, 100.0D).scale(0.42D);

        Vec3 limited = RagdollJointLimits.limitNeck(requested, RagdollJointLimits.Frame.IDENTITY);

        assertEquals(0.42D, limited.length(), 1.0E-9D);
    }

    @Test
    void productionHipLimitsClampSwingAndMirrorInwardTravel() {
        RagdollJointLimits.Limited forward = hip(direction(0.0D, 120.0D), true);
        RagdollJointLimits.Limited backward = hip(direction(0.0D, -120.0D), false);
        RagdollJointLimits.Limited leftInward = hip(direction(-80.0D, 0.0D), true);
        RagdollJointLimits.Limited rightInward = hip(direction(80.0D, 0.0D), false);
        RagdollJointLimits.Limited leftOutward = hip(direction(130.0D, 0.0D), true);
        RagdollJointLimits.Limited rightOutward = hip(direction(-130.0D, 0.0D), false);

        assertEquals(85.0D, Math.toDegrees(forward.swingAngle()), 1.0E-6D);
        assertEquals(-85.0D, Math.toDegrees(backward.swingAngle()), 1.0E-6D);
        assertEquals(-15.0D, Math.toDegrees(leftInward.lateralAngle()), 1.0E-6D);
        assertEquals(15.0D, Math.toDegrees(rightInward.lateralAngle()), 1.0E-6D);
        assertEquals(110.0D, Math.toDegrees(leftOutward.lateralAngle()), 1.0E-6D);
        assertEquals(-110.0D, Math.toDegrees(rightOutward.lateralAngle()), 1.0E-6D);
        assertEquals(1.0D, forward.direction().length(), 1.0E-8D);
        assertEquals(1.0D, backward.direction().length(), 1.0E-8D);
    }

    @Test
    void theArmTorsoClearanceScaleTightensTheShoulderInwardLimit() {
        // The tuning slider that used to scale the removed repulsion pass now scales the angular
        // limit. Turning clearance up must let the arm cross less, not more.
        double loose = RagdollJointLimits.shoulderInwardLimit(0.50D);
        double normal = RagdollJointLimits.shoulderInwardLimit(1.0D);
        double tight = RagdollJointLimits.shoulderInwardLimit(2.0D);

        assertEquals(RagdollJointLimits.SHOULDER_INWARD_LIMIT, normal, 1.0E-12D);
        assertTrue(loose > normal, () -> "0.5 scale did not loosen: " + Math.toDegrees(loose));
        assertTrue(tight < normal, () -> "2.0 scale did not tighten: " + Math.toDegrees(tight));
    }

    @Test
    void anOutOfRangeClearanceScaleCannotInvertTheInwardLimit() {
        // A negative or NaN scale reaching the solver would otherwise turn the inward bound into an
        // outward one and pin both arms away from the body for the whole grab.
        for (double scale : new double[] {
                -5.0D, 0.0D, 1.0E9D, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double limit = RagdollJointLimits.shoulderInwardLimit(scale);

            assertTrue(limit >= 0.0D && Double.isFinite(limit),
                    () -> "scale " + scale + " produced limit " + limit);
            assertTrue(limit <= RagdollJointLimits.SHOULDER_INWARD_LIMIT * 2.0D + 1.0E-9D,
                    () -> "scale " + scale + " escaped the configured range: "
                            + Math.toDegrees(limit));
        }
    }

    @Test
    void productionShoulderLimitsAreWiderThanTheHips() {
        RagdollJointLimits.Limited leftOutward = shoulder(direction(179.0D, 0.0D), true);
        RagdollJointLimits.Limited leftInward = shoulder(direction(-80.0D, 0.0D), true);
        RagdollJointLimits.Limited forward = shoulder(direction(0.0D, 89.0D), true);

        assertEquals(170.0D, Math.toDegrees(leftOutward.lateralAngle()), 1.0E-6D);
        assertEquals(-35.0D, Math.toDegrees(leftInward.lateralAngle()), 1.0E-6D);
        assertEquals(89.0D, Math.toDegrees(forward.swingAngle()), 1.0E-6D);
        assertTrue(RagdollJointLimits.SHOULDER_OUTWARD_LIMIT
                > RagdollJointLimits.HIP_OUTWARD_LIMIT);
        assertTrue(RagdollJointLimits.SHOULDER_SWING_LIMIT
                > RagdollJointLimits.HIP_SWING_LIMIT);
    }

    @Test
    void aSwingLimitPastNinetyDegreesCannotDefeatTheInwardLimit() {
        // cos(swing) flips sign past 90 degrees, mirroring the lateral axis: a caller asking for a
        // 150-degree swing range would otherwise gain an unlimited inward crossing for free.
        RagdollJointLimits.Limited left = RagdollJointLimits.limitRootBone(
                direction(-100.0D, 0.0D), RagdollJointLimits.Frame.IDENTITY, true,
                INWARD, OUTWARD, Math.toRadians(150.0D), 0.0D, 0.0D);

        assertEquals(-10.0D, Math.toDegrees(left.lateralAngle()), 1.0E-6D);
        assertTrue(Math.abs(left.swingAngle()) <= Math.toRadians(90.0D) + 1.0E-9D,
                () -> "swing escaped the hard bound: " + Math.toDegrees(left.swingAngle()));
    }

    @Test
    void kneeAndElbowMayNotHyperextendUnderProductionLimits() {
        Vec3 root = Vec3.ZERO;
        Vec3 joint = new Vec3(0.0D, -1.0D, 0.0D);
        Vec3 backwardFold = joint.add(new Vec3(0.0D, -0.60D, -0.80D));

        Vec3 knee = RagdollJointLimits.limitHinge(root, joint, backwardFold,
                RagdollJointLimits.KNEE_MINIMUM_FLEXION,
                RagdollJointLimits.KNEE_MAXIMUM_FLEXION,
                new Vec3(0.0D, 0.0D, -1.0D));
        Vec3 elbow = RagdollJointLimits.limitHinge(root, joint,
                joint.add(new Vec3(0.0D, -0.60D, 0.80D)),
                RagdollJointLimits.ELBOW_MINIMUM_FLEXION,
                RagdollJointLimits.ELBOW_MAXIMUM_FLEXION,
                new Vec3(0.0D, 0.0D, 1.0D));

        // The knee folds backward and the elbow forward, so each rejects the opposite side.
        assertTrue(knee.subtract(joint).z < 0.0D, () -> "knee hyperextended: " + knee);
        assertTrue(elbow.subtract(joint).z > 0.0D, () -> "elbow hyperextended: " + elbow);
    }

    private static RagdollJointLimits.Limited hip(Vec3 direction, boolean leftSide) {
        return RagdollJointLimits.limitRootBone(direction,
                RagdollJointLimits.Frame.IDENTITY, leftSide,
                RagdollJointLimits.HIP_INWARD_LIMIT,
                RagdollJointLimits.HIP_OUTWARD_LIMIT,
                RagdollJointLimits.HIP_SWING_LIMIT, 0.0D, 0.0D);
    }

    private static RagdollJointLimits.Limited shoulder(Vec3 direction, boolean leftSide) {
        return RagdollJointLimits.limitRootBone(direction,
                RagdollJointLimits.Frame.IDENTITY, leftSide,
                RagdollJointLimits.SHOULDER_INWARD_LIMIT,
                RagdollJointLimits.SHOULDER_OUTWARD_LIMIT,
                RagdollJointLimits.SHOULDER_SWING_LIMIT, 0.0D, 0.0D);
    }

    private static RagdollJointLimits.Limited limit(Vec3 direction, boolean leftSide) {
        return RagdollJointLimits.limitRootBone(direction,
                RagdollJointLimits.Frame.IDENTITY, leftSide,
                INWARD, OUTWARD, SWING, 0.0D, 0.0D);
    }

    /** Builds a unit limb direction from a lateral angle and a forward swing angle, in degrees. */
    private static Vec3 direction(double lateralDegrees, double swingDegrees) {
        double lateral = Math.toRadians(lateralDegrees);
        double swing = Math.toRadians(swingDegrees);
        double planar = Math.cos(swing);
        return new Vec3(
                Math.sin(lateral) * planar,
                -Math.cos(lateral) * planar,
                Math.sin(swing));
    }

    /** Builds a unit neck direction from a roll angle and a pitch angle, in degrees. */
    private static Vec3 neckDirection(double rollDegrees, double pitchDegrees) {
        double roll = Math.toRadians(rollDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double planar = Math.cos(pitch);
        return new Vec3(
                Math.sin(roll) * planar,
                Math.cos(roll) * planar,
                Math.sin(pitch));
    }
}
