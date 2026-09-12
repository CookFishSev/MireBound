package com.fish.mirebound.stain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class WallStainGeometryTest {
    @Test
    void gapsInTheSupportFaceStayEmptyForBothContactAndFlow() {
        List<AABB> shape = List.of(new AABB(0, 0, 0, 0.4, 1, 1), new AABB(0.6, 0, 0, 1, 1, 1));
        for (Direction face : List.of(Direction.NORTH, Direction.SOUTH)) {
            assertTrue(WallStainGeometry.contains(shape, face, 0.25, 0.5));
            assertFalse(WallStainGeometry.contains(shape, face, 0.5, 0.5));
            assertTrue(WallStainGeometry.contains(shape, face, 0.75, 0.5));
        }
    }

    @Test
    void aShapeOnTheOppositeSideDoesNotSupportAnAdjacentFace() {
        List<AABB> inset = List.of(new AABB(0.25, 0, 0, 1, 1, 1));
        assertFalse(WallStainGeometry.contains(inset, Direction.WEST, 0.5, 0.5));
        assertTrue(WallStainGeometry.contains(inset, Direction.EAST, 0.5, 0.5));
        assertFalse(WallStainGeometry.contains(inset, Direction.UP, 0.1, 0.5));
        assertTrue(WallStainGeometry.contains(inset, Direction.UP, 0.75, 0.5));
    }
}
