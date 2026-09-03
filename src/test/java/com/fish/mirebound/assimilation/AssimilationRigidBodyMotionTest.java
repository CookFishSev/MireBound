package com.fish.mirebound.assimilation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AssimilationRigidBodyMotionTest {
    @Test
    void gravityAndDragProduceBoundedDownwardMotion() {
        Vec3 velocity = Vec3.ZERO;
        for (int tick = 0; tick < 200; tick++) {
            velocity = AssimilationRigidBodyMotion.integrate(velocity, 0.08F, 0.98F, 1.5F);
        }
        assertTrue(velocity.y < 0.0D);
        assertTrue(velocity.length() <= 1.50001D);
    }

    @Test
    void floorCollisionStopsTinyBounceAndAppliesFriction() {
        AssimilationRigidBodyMotion.CollisionResult result =
                AssimilationRigidBodyMotion.resolveCollision(
                        new Vec3(0.5D, -0.10D, -0.25D),
                        new Vec3(0.5D, 0.0D, -0.25D), 0.08F, 0.5F);
        assertTrue(result.blockedY());
        assertEquals(0.25D, result.velocity().x, 1.0E-6D);
        assertEquals(0.0D, result.velocity().y, 1.0E-6D);
        assertEquals(-0.125D, result.velocity().z, 1.0E-6D);
    }

    @Test
    void unobstructedMotionIsPreserved() {
        Vec3 motion = new Vec3(0.2D, -0.4D, 0.1D);
        AssimilationRigidBodyMotion.CollisionResult result =
                AssimilationRigidBodyMotion.resolveCollision(motion, motion, 0.2F, 0.4F);
        assertFalse(result.blockedX());
        assertFalse(result.blockedY());
        assertFalse(result.blockedZ());
        assertEquals(motion, result.velocity());
    }

    @Test
    void visualTiltConvergesWithoutExceedingConfiguredLimit() {
        float pitch = 0.0F;
        float roll = 0.0F;
        for (int tick = 0; tick < 80; tick++) {
            AssimilationRigidBodyMotion.Tilt next = AssimilationRigidBodyMotion.updateTilt(
                    pitch, roll, new Vec3(4.0D, 0.0D, 3.0D),
                    0.0F, 3.0F, 18.0F, 0.2F, false);
            pitch = next.pitch();
            roll = next.roll();
        }
        assertTrue(Math.abs(pitch) <= 18.0001F);
        assertTrue(Math.abs(roll) <= 18.0001F);
        assertTrue(Math.abs(pitch) > 1.0F || Math.abs(roll) > 1.0F);
    }

}
