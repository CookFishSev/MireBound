package com.fish.mirebound.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class TentacleRaycastTest {
    @Test
    void sphereRaycastReturnsTheNearRootSurface() {
        TentacleRaycast.SphereHit hit = TentacleRaycast.raycastSphere(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), 20.0D,
                new Vec3(0.0D, 0.0D, 6.0D), 0.75D);

        assertNotNull(hit);
        assertEquals(5.25D, hit.rayDistance(), 1.0E-6D);
        assertEquals(5.25D, hit.surfacePosition().z, 1.0E-6D);
    }

    @Test
    void sphereRaycastRejectsARootOutsideItsRadius() {
        assertNull(TentacleRaycast.raycastSphere(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), 20.0D,
                new Vec3(1.0D, 0.0D, 6.0D), 0.75D));
    }

    @Test
    void pointValidationAcceptsTheRenderedSurface() {
        assertTrue(TentacleRaycast.containsPoint(
                List.of(new Vec3(0.0D, 0.0D, 0.0D),
                        new Vec3(0.0D, 4.0D, 0.0D)),
                new Vec3(0.65D, 2.0D, 0.0D), ignored -> 0.5D, 0.2D));
    }
}
