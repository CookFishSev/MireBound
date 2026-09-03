package com.fish.mirebound.client.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class TentacleGrabPlayerRendererTest {
    private static final double EPSILON = 1.0E-5D;

    @Test
    void modelLegLateralSignsMatchTheRagdollSemantics() {
        assertEquals(10.0D, degrees(
                TentacleModelPartRotations.legEuler(legDirection(-10.0D, 0.0D)).z), EPSILON);
        assertEquals(-100.0D, degrees(
                TentacleModelPartRotations.legEuler(legDirection(100.0D, 0.0D)).z), EPSILON);
        assertEquals(-10.0D, degrees(
                TentacleModelPartRotations.legEuler(legDirection(10.0D, 0.0D)).z), EPSILON);
        assertEquals(100.0D, degrees(
                TentacleModelPartRotations.legEuler(legDirection(-100.0D, 0.0D)).z), EPSILON);
    }

    @Test
    void combinedLargeLegSwingsRoundTripWithoutEulerBranchDistortion() {
        Vector3f previous = null;
        for (int step = 0; step <= 100; step++) {
            double amount = step / 100.0D;
            double lateral = -10.0D + 110.0D * amount;
            double forward = -75.0D + 150.0D * amount;
            Vec3 expected = legDirection(lateral, forward);
            Vector3f angles = TentacleModelPartRotations.legEuler(expected);
            Vec3 rendered = renderedRagdollDirection(angles);

            assertEquals(expected.x, rendered.x, EPSILON);
            assertEquals(expected.y, rendered.y, EPSILON);
            assertEquals(expected.z, rendered.z, EPSILON);
            assertEquals(0.0F, angles.y, 0.0F);
            if (previous != null) {
                assertTrue(Math.abs(angles.x - previous.x) < Math.toRadians(2.0D));
                assertTrue(Math.abs(angles.z - previous.z) < Math.toRadians(2.0D));
            }
            previous = angles;
        }
    }

    @Test
    void applyingALargeLegSwingNeverMovesTheHipPivot() {
        ModelPart leg = new ModelPart(List.of(), Map.of());
        leg.setPos(1.90F, 12.0F, 0.0F);

        TentacleGrabPlayerRenderer.applyLegDirection(
                leg, legDirection(100.0D, 75.0D));

        assertEquals(1.90F, leg.x, 0.0F);
        assertEquals(12.0F, leg.y, 0.0F);
        assertEquals(0.0F, leg.z, 0.0F);
        assertEquals(0.0F, leg.yRot, 0.0F);
    }

    @Test
    void armLongAxisRotationStaysLockedWhileBothSwingAxesMove() {
        Vector3f previous = null;
        for (int step = 0; step <= 100; step++) {
            double amount = step / 100.0D;
            Vec3 expected = legDirection(
                    -85.0D + 170.0D * amount,
                    -70.0D + 140.0D * amount);
            Vector3f angles = TentacleModelPartRotations.armEuler(expected);
            Vec3 rendered = renderedRagdollDirection(angles);

            assertEquals(expected.x, rendered.x, EPSILON);
            assertEquals(expected.y, rendered.y, EPSILON);
            assertEquals(expected.z, rendered.z, EPSILON);
            assertEquals(0.0F, angles.y, 0.0F,
                    "rotation around the arm's long axis must stay locked");
            if (previous != null) {
                assertTrue(Math.abs(angles.x - previous.x) < Math.toRadians(2.0D));
                assertTrue(Math.abs(angles.z - previous.z) < Math.toRadians(2.0D));
            }
            previous = angles;
        }
    }

    @Test
    void applyingArmSwingDoesNotMoveTheShoulderPivot() {
        ModelPart arm = new ModelPart(List.of(), Map.of());
        arm.setPos(5.0F, 2.0F, 0.0F);

        TentacleGrabPlayerRenderer.applyArmDirection(
                arm, legDirection(70.0D, -55.0D));

        assertEquals(5.0F, arm.x, 0.0F);
        assertEquals(2.0F, arm.y, 0.0F);
        assertEquals(0.0F, arm.z, 0.0F);
        assertEquals(0.0F, arm.yRot, 0.0F);
    }

    private static Vec3 legDirection(double lateralDegrees, double forwardDegrees) {
        double lateral = Math.toRadians(lateralDegrees);
        double forward = Math.toRadians(forwardDegrees);
        double planar = Math.cos(forward);
        return new Vec3(
                Math.sin(lateral) * planar,
                -Math.cos(lateral) * planar,
                Math.sin(forward));
    }

    private static Vec3 renderedRagdollDirection(Vector3f angles) {
        Vector3f modelDirection = new Quaternionf()
                .rotationZYX(angles.z, angles.y, angles.x)
                .transform(new Vector3f(0.0F, 1.0F, 0.0F));
        return new Vec3(
                modelDirection.x, -modelDirection.y, -modelDirection.z);
    }

    private static double degrees(float radians) {
        return Math.toDegrees(radians);
    }
}
