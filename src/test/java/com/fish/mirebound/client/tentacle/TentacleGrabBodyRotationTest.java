package com.fish.mirebound.client.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Covers the whole-body rotation's pivot, which is what decides whether a spun player stays in the
 * tentacle's grip or swings away from it.
 */
class TentacleGrabBodyRotationTest {
    private static final float HEIGHT = 1.80F;

    @Test
    void theBodyCentreIsTheFixedPointOfTheRotation() {
        PoseStack poseStack = new PoseStack();
        TentacleGrabPlayerRenderer.rotateAboutCentre(poseStack,
                new Quaternionf().rotateX((float) Math.PI), HEIGHT, 1.0F);

        // The centre must come out exactly where it went in: anything else is the body orbiting its
        // own feet, which reads as the player being flung around rather than held.
        Vector3f centre = transform(poseStack, 0.0F, HEIGHT * 0.5F, 0.0F);
        assertEquals(0.0F, centre.x, 1.0E-5F);
        assertEquals(HEIGHT * 0.5F, centre.y, 1.0E-5F);
        assertEquals(0.0F, centre.z, 1.0E-5F);
    }

    @Test
    void aFullInversionSwapsHeadAndFeet() {
        PoseStack poseStack = new PoseStack();
        TentacleGrabPlayerRenderer.rotateAboutCentre(poseStack,
                new Quaternionf().rotateX((float) Math.PI), HEIGHT, 1.0F);

        Vector3f head = transform(poseStack, 0.0F, HEIGHT, 0.0F);
        Vector3f feet = transform(poseStack, 0.0F, 0.0F, 0.0F);

        assertEquals(0.0F, head.y, 1.0E-5F);
        assertEquals(HEIGHT, feet.y, 1.0E-5F);
    }

    @Test
    void theEntityScaleIsDividedOutOfThePivot() {
        // setupRotations runs with the entity scale already on the stack. The pivot is a block
        // offset, so it has to be pre-divided or it lands at twice the intended height.
        float scale = 2.0F;
        PoseStack poseStack = new PoseStack();
        poseStack.scale(scale, scale, scale);
        TentacleGrabPlayerRenderer.rotateAboutCentre(poseStack,
                new Quaternionf().rotateZ((float) Math.PI), HEIGHT, scale);

        // In final (post-scale) space the fixed point must still be the scaled body centre.
        Vector3f centre = transform(poseStack, 0.0F, HEIGHT * 0.5F / scale, 0.0F);
        assertEquals(0.0F, centre.x, 1.0E-5F);
        assertEquals(HEIGHT * 0.5F, centre.y, 1.0E-5F);
    }

    @Test
    void aDegenerateScaleCannotProduceANonFinitePivot() {
        // Entity scale can legitimately reach zero mid-transition. A raw division would put infinity
        // into the pose matrix and the player would disappear rather than simply not rotate.
        PoseStack poseStack = new PoseStack();
        TentacleGrabPlayerRenderer.rotateAboutCentre(poseStack,
                new Quaternionf().rotateY(0.70F), HEIGHT, 0.0F);

        Vector3f probe = transform(poseStack, 0.30F, 0.90F, 0.10F);
        assertTrue(Float.isFinite(probe.x) && Float.isFinite(probe.y) && Float.isFinite(probe.z),
                "degenerate scale produced a non-finite transform: " + probe);
    }

    @Test
    void anIdentityOrientationLeavesTheStackUnchanged() {
        PoseStack rotated = new PoseStack();
        TentacleGrabPlayerRenderer.rotateAboutCentre(rotated, new Quaternionf(), HEIGHT, 1.0F);

        Vector3f probe = transform(rotated, 0.25F, 1.40F, -0.35F);
        assertEquals(0.25F, probe.x, 1.0E-5F);
        assertEquals(1.40F, probe.y, 1.0E-5F);
        assertEquals(-0.35F, probe.z, 1.0E-5F);
    }

    private static Vector3f transform(PoseStack poseStack, float x, float y, float z) {
        return poseStack.last().pose().transformPosition(new Vector3f(x, y, z));
    }
}
