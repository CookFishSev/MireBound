package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AnimatedPlayerGeometryTest {
    @Test
    void capturedGeometryFollowsTheCurrentPlayerOrigin() {
        Vec3 capturedOrigin = new Vec3(10.0D, 64.0D, -4.0D);
        Vec3 capturedCenter = capturedOrigin.add(0.25D, 1.5D, -0.10D);
        Vec3 currentOrigin = new Vec3(12.75D, 63.5D, 3.0D);

        Vec3 reanchored = AnimatedPlayerGeometry.reanchor(
                capturedCenter, capturedOrigin, currentOrigin);

        Vec3 expected = currentOrigin.add(0.25D, 1.5D, -0.10D);
        assertEquals(expected.x, reanchored.x, 1.0E-9D);
        assertEquals(expected.y, reanchored.y, 1.0E-9D);
        assertEquals(expected.z, reanchored.z, 1.0E-9D);
    }
}
