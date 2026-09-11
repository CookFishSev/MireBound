package com.fish.mirebound.rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RopeLassoPlacementTest {
    private static final BlockPos TARGET = new BlockPos(0, 1, 0);
    private static final AABB CUBE = new AABB(0, 1, 0, 1, 2, 1);
    private static final Vec3 CENTER = new Vec3(0.5, 1.5, 0.5);

    @Test
    void uprightPostClearsStackedBlocksForEveryThrowBearing() {
        Vec3[] axes = RopeRuntime.lassoAxes(CUBE, false, false);
        for (int bearing = 0; bearing < 16; bearing++) {
            Vec3[] loop = RopeRuntime.lassoPoints(
                    CENTER, axes[0], axes[1], bearing * Math.PI / 8);
            for (Vec3 point : loop) {
                assertEquals(CENTER.y, point.y, 1.0E-9);
            }
            assertFixedLoop(loop);
            assertTrue(RopeRuntime.lassoIsClear(TARGET, loop,
                    (pos, point) -> pos.getX() == 0 && pos.getZ() == 0));
        }
    }

    @Test
    void horizontalBeamsKeepVerticalLoopsInBothDirections() {
        for (boolean alongX : new boolean[] {true, false}) {
            Vec3[] axes = RopeRuntime.lassoAxes(CUBE, alongX, !alongX);
            Vec3[] loop = RopeRuntime.lassoPoints(CENTER, axes[0], axes[1], 0);
            for (Vec3 point : loop) {
                assertEquals(0.5, alongX ? point.x : point.z, 1.0E-9);
            }
            assertTrue(java.util.Arrays.stream(loop).anyMatch(p -> p.y > CUBE.maxY));
            assertTrue(java.util.Arrays.stream(loop).anyMatch(p -> p.y < CUBE.minY));
            assertFixedLoop(loop);
            assertTrue(RopeRuntime.lassoIsClear(TARGET, loop, (pos, point) ->
                    pos.getY() == 1 && (alongX ? pos.getZ() == 0 : pos.getX() == 0)));
        }
    }

    @Test
    void narrowUprightPostStillUsesHorizontalLoop() {
        Vec3[] axes = RopeRuntime.lassoAxes(new AABB(0.375, 0, 0.375, 0.625, 1.5, 0.625),
                false, false);
        assertEquals(0, axes[0].y, 1.0E-9);
        assertEquals(0, axes[1].y, 1.0E-9);
    }

    @Test
    void loopEnteringGroundOrOtherBlocksIsStillRejected() {
        Vec3[] upright = RopeRuntime.lassoAxes(CUBE, false, false);
        Vec3[] horizontal = RopeRuntime.lassoAxes(CUBE, true, false);
        assertFalse(RopeRuntime.lassoIsClear(TARGET,
                RopeRuntime.lassoPoints(CENTER, upright[0], upright[1], 0),
                (pos, point) -> true));
        assertFalse(RopeRuntime.lassoIsClear(TARGET,
                RopeRuntime.lassoPoints(CENTER, horizontal[0], horizontal[1], 0),
                (pos, point) -> pos.getY() < 1));
    }

    private static void assertFixedLoop(Vec3[] loop) {
        assertEquals(6, loop.length);
        assertEquals(loop[0], loop[5]);
        for (int segment = 0; segment < 5; segment++) {
            assertEquals(RopeProperties.DEFAULT.segmentLength(),
                    loop[segment].distanceTo(loop[segment + 1]), 1.0E-9);
        }
    }
}
