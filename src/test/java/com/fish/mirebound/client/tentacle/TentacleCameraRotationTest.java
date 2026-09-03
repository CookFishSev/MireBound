package com.fish.mirebound.client.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class TentacleCameraRotationTest {
    @Test
    void closestEulerBranchStaysContinuousAcrossGimbalBoundary() {
        Vector3f previous = null;
        for (int step = 0; step <= 80; step++) {
            float middle = 1.20F + step * 0.01F;
            Quaternionf expected = new Quaternionf().rotationXYZ(0.35F, middle, -0.22F);
            Vector3f current = TentacleCameraRotation.closestEulerXyz(
                    expected, previous, new Vector3f());
            Quaternionf reconstructed = new Quaternionf().rotationXYZ(
                    current.x, current.y, current.z);

            assertTrue(Math.abs(expected.dot(reconstructed)) > 0.9999F,
                    () -> "Euler branch changed the rotation: " + current);
            if (previous != null) {
                assertTrue(current.distance(previous) < 0.10F,
                        "Euler branch jumped: previous=" + previous + " current=" + current);
            }
            previous = current;
        }
    }

    @Test
    void quaternionSignDoesNotCreateACameraJump() {
        Quaternionf orientation = new Quaternionf().rotationXYZ(0.4F, 1.1F, -0.7F);
        Vector3f first = TentacleCameraRotation.closestEulerXyz(
                orientation, null, new Vector3f());
        Quaternionf negated = new Quaternionf(
                -orientation.x, -orientation.y, -orientation.z, -orientation.w);
        Vector3f second = TentacleCameraRotation.closestEulerXyz(
                negated, first, new Vector3f());

        assertTrue(first.distance(second) < 1.0E-5F,
                () -> "same quaternion rotation selected another Euler branch");
    }

    @Test
    void minecraftCameraYxzConversionRoundTripsLargeRotations() {
        float[][] samples = {
                {0.0F, 0.0F, 0.0F},
                {127.0F, 72.0F, -43.0F},
                {-164.0F, -81.0F, 128.0F},
                {315.0F, 94.0F, -201.0F}
        };
        for (float[] sample : samples) {
            Quaternionf expected = TentacleCameraRotation.fromCameraAngles(
                    sample[0], sample[1], sample[2], new Quaternionf());
            Vector3f yxz = TentacleCameraRotation.closestEulerYxz(
                    expected, null, new Vector3f());
            Quaternionf reconstructed = new Quaternionf().rotationYXZ(
                    yxz.y, yxz.x, yxz.z);
            assertTrue(Math.abs(expected.dot(reconstructed)) > 0.9999F,
                    () -> "camera YXZ conversion changed the rotation: " + yxz);
        }
    }

    @Test
    void ragdollCameraCompositionUsesFixedGrabReference() {
        Quaternionf referenceBody = new Quaternionf().rotationY((float) Math.toRadians(73.0D));
        assertTrue(Math.abs(TentacleCameraRotation.minecraftYaw(referenceBody) - 73.0F)
                < 1.0E-4F);
        Quaternionf referenceCamera = TentacleCameraRotation.fromCameraAngles(
                73.0F, -24.0F, 0.0F, new Quaternionf());
        Quaternionf relativeHead = new Quaternionf().rotationXYZ(
                1.25F, -0.82F, 2.15F);
        Quaternionf composed = TentacleCameraRotation.composeRagdollCamera(
                referenceBody, referenceCamera, relativeHead, 1.0F, new Quaternionf());
        Quaternionf localCamera = new Quaternionf(referenceBody).conjugate()
                .mul(referenceCamera);
        Quaternionf expected = new Quaternionf(referenceBody)
                .mul(relativeHead).mul(localCamera).normalize();

        assertTrue(Math.abs(expected.dot(composed)) > 0.99999F);

        Quaternionf unrelatedLiveView = TentacleCameraRotation.fromCameraAngles(
                -151.0F, 67.0F, 0.0F, new Quaternionf());
        Quaternionf recomposed = TentacleCameraRotation.composeRagdollCamera(
                referenceBody, referenceCamera, relativeHead, 1.0F, new Quaternionf());
        assertTrue(Math.abs(composed.dot(recomposed)) > 0.99999F,
                "live player camera changes must not rotate an already captured ragdoll camera");
        assertTrue(Math.abs(composed.dot(unrelatedLiveView)) < 0.999F);
    }

    @Test
    void cameraYxzBranchStaysContinuousThroughInversion() {
        Vector3f previous = null;
        for (int step = 0; step <= 160; step++) {
            float phase = step / 160.0F;
            Quaternionf expected = new Quaternionf().rotationYXZ(
                    -2.4F + phase * 4.8F,
                    1.15F + phase * 0.85F,
                    -0.7F + phase * 1.4F);
            Vector3f current = TentacleCameraRotation.closestEulerYxz(
                    expected, previous, new Vector3f());
            Quaternionf reconstructed = new Quaternionf().rotationYXZ(
                    current.y, current.x, current.z);
            assertTrue(Math.abs(expected.dot(reconstructed)) > 0.9999F);
            if (previous != null) {
                assertTrue(current.distance(previous) < 0.12F,
                        "camera branch jumped: previous=" + previous + " current=" + current);
            }
            previous = current;
        }
    }

    @Test
    void liveViewDeltaRoundTripsVanillaCameraWhenTheRagdollIsUnchanged() {
        Quaternionf reference = TentacleCameraRotation.fromCameraAngles(
                35.0F, -20.0F, 0.0F, new Quaternionf());
        Quaternionf referenceInput = TentacleCameraRotation.fromCameraAngles(
                35.0F, -20.0F, 0.0F, new Quaternionf());
        Quaternionf currentInput = TentacleCameraRotation.fromCameraAngles(
                60.0F, 10.0F, 0.0F, new Quaternionf());
        Quaternionf adjusted = TentacleCameraRotation.composeViewInput(
                reference, referenceInput, currentInput, new Quaternionf());
        Vector3f angles = TentacleCameraRotation.closestEulerYxz(
                adjusted, null, new Vector3f());

        assertEquals(60.0F, 180.0F - (float) Math.toDegrees(angles.y), 1.0E-3F);
        assertEquals(10.0F, -(float) Math.toDegrees(angles.x), 1.0E-3F);
        assertEquals(0.0F, -(float) Math.toDegrees(angles.z), 1.0E-3F);
    }

    @Test
    void mouseLookOffsetsRemainContinuousWhileTheRagdollIsInverted() {
        Quaternionf referenceInput = TentacleCameraRotation.fromCameraAngles(
                -80.0F, -45.0F, 0.0F, new Quaternionf());
        Vector3f previousFinal = null;
        for (int step = 0; step <= 120; step++) {
            float amount = step / 120.0F;
            Quaternionf driven = new Quaternionf().rotationYXZ(
                    -1.3F + amount * 2.6F,
                    1.35F + amount * 0.45F,
                    -0.9F + amount * 1.8F);
            Quaternionf currentInput = TentacleCameraRotation.fromCameraAngles(
                    -80.0F + amount * 160.0F,
                    -45.0F + amount * 90.0F, 0.0F, new Quaternionf());
            Quaternionf adjusted = TentacleCameraRotation.composeViewInput(
                    driven, referenceInput, currentInput, new Quaternionf());
            Vector3f current = TentacleCameraRotation.closestEulerYxz(
                    adjusted, previousFinal, new Vector3f());
            Quaternionf reconstructed = new Quaternionf().rotationYXZ(
                    current.y, current.x, current.z);

            assertTrue(Math.abs(adjusted.dot(reconstructed)) > 0.9999F);
            if (previousFinal != null) {
                assertTrue(current.distance(previousFinal) < 0.22F,
                        "mouse look introduced an Euler branch jump: previous="
                                + previousFinal + " current=" + current
                                + " distance=" + current.distance(previousFinal));
            }
            previousFinal = current;
        }
    }

    @Test
    void mouseLookIsAppliedInTheTiltedHeadLocalSpace() {
        Quaternionf referenceInput = TentacleCameraRotation.fromCameraAngles(
                28.0F, -17.0F, 0.0F, new Quaternionf());
        Quaternionf currentInput = TentacleCameraRotation.fromCameraAngles(
                91.0F, 34.0F, 0.0F, new Quaternionf());
        Quaternionf headMotion = new Quaternionf().rotationYXZ(
                0.72F, 1.18F, -0.93F);
        Quaternionf driven = new Quaternionf(headMotion)
                .mul(referenceInput).normalize();

        Quaternionf adjusted = TentacleCameraRotation.composeViewInput(
                driven, referenceInput, currentInput, new Quaternionf());
        Quaternionf expected = new Quaternionf(headMotion)
                .mul(currentInput).normalize();

        assertTrue(Math.abs(adjusted.dot(expected)) > 0.99999F,
                "mouse input must remain attached to the rolled ragdoll head axes");
    }

    @Test
    void cameraForwardMatchesMinecraftDirectionFromFinalAngles() {
        Quaternionf camera = TentacleCameraRotation.fromCameraAngles(
                -137.0F, 63.0F, -48.0F, new Quaternionf());
        Vector3f forward = TentacleCameraRotation.cameraForward(
                camera, new Vector3f());
        Vector3f angles = TentacleCameraRotation.closestEulerYxz(
                camera, null, new Vector3f());
        var minecraftDirection = net.minecraft.world.phys.Vec3.directionFromRotation(
                -(float) Math.toDegrees(angles.x),
                180.0F - (float) Math.toDegrees(angles.y));

        assertEquals(minecraftDirection.x, forward.x, 1.0E-4D);
        assertEquals(minecraftDirection.y, forward.y, 1.0E-4D);
        assertEquals(minecraftDirection.z, forward.z, 1.0E-4D);
    }
}
