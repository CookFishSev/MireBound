package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudTuningTargetRendererTest {
    @Test
    void rotatingSummonBoxKeepsItsCenterAndRequestedScale() {
        Vec3[] corners = corners(new Vec3(4.0D, 7.0D, -3.0D), 0.5D);

        Vec3[] rotated = MudTuningTargetRenderer.rotateBox(
                corners, 0.72D, 0.8F, 0.5F, -0.3F);

        Vec3 originalCenter = center(corners);
        Vec3 rotatedCenter = center(rotated);
        assertEquals(originalCenter.x, rotatedCenter.x, 1.0E-6D);
        assertEquals(originalCenter.y, rotatedCenter.y, 1.0E-6D);
        assertEquals(originalCenter.z, rotatedCenter.z, 1.0E-6D);
        assertEquals(corners[0].distanceTo(originalCenter) * 0.72D,
                rotated[0].distanceTo(rotatedCenter), 1.0E-6D);
    }

    private static Vec3[] corners(Vec3 center, double halfSize) {
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            corners[index] = center.add(
                    (index & 1) == 0 ? -halfSize : halfSize,
                    (index & 2) == 0 ? -halfSize : halfSize,
                    (index & 4) == 0 ? -halfSize : halfSize);
        }
        return corners;
    }

    private static Vec3 center(Vec3[] corners) {
        Vec3 center = Vec3.ZERO;
        for (Vec3 corner : corners) {
            center = center.add(corner);
        }
        return center.scale(1.0D / corners.length);
    }
}
