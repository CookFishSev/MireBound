package com.fish.mirebound.client.itemphysics;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.minecraft.util.Mth;

class DroppedItemVisualPoseTest {
    private static final UUID ITEM_UUID =
            UUID.fromString("4bde7625-6ba0-4bea-befd-c998762f61aa");

    @Test
    void poseIsStableAndBoundedByConfiguredTilt() {
        DroppedItemVisualPose first = DroppedItemVisualPose.create(ITEM_UUID, 24.0F);
        DroppedItemVisualPose second = DroppedItemVisualPose.create(ITEM_UUID, 24.0F);
        DroppedItemVisualPose other = DroppedItemVisualPose.create(
                UUID.fromString("a109235f-516d-4e0e-bacb-00e92a14f681"), 24.0F);

        assertEquals(first, second);
        assertNotEquals(first, other);
        double tilt = Math.hypot(first.tiltXDegrees(), first.tiltZDegrees());
        assertTrue(tilt >= 24.0D * 0.45D - 1.0E-5D);
        assertTrue(tilt <= 24.0D + 1.0E-5D);
    }

    @Test
    void presentationUsesSmoothBoundedSettling() {
        assertEquals(0.0F, DroppedItemVisualPose.easedAmount(-2.0F, 6), 1.0E-6F);
        assertEquals(0.5F, DroppedItemVisualPose.easedAmount(3.0F, 6), 1.0E-6F);
        assertEquals(1.0F, DroppedItemVisualPose.easedAmount(8.0F, 6), 1.0E-6F);
        assertEquals(1.0F, DroppedItemVisualPose.easedAmount(0.0F, 0), 1.0E-6F);
    }

    @Test
    void stableYawStaysCloseToTheItemOrientation() {
        float referenceYaw = 2.4F;
        DroppedItemVisualPose pose = DroppedItemVisualPose.create(
                ITEM_UUID, 8.0F, referenceYaw);

        float deltaDegrees = Math.abs(Mth.wrapDegrees(
                (pose.yawRadians() - referenceYaw) * Mth.RAD_TO_DEG));
        assertTrue(deltaDegrees <= DroppedItemVisualPose.MAXIMUM_RANDOM_YAW_DEGREES);
    }
}
