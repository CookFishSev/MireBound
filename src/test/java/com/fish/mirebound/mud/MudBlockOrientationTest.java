package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudBlockOrientationTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void attachmentDirectionRotatesTheBaseLayerAroundTheBlock() {
        Vec3 support = new Vec3(0.25D, 0.0D, 0.75D);
        Vec3 exposed = new Vec3(0.25D, 0.125D, 0.75D);

        assertEquals(0.0D,
                MudOrientation.orientPoint(support, Direction.EAST).x,
                EPSILON);
        assertEquals(0.125D,
                MudOrientation.orientPoint(exposed, Direction.EAST).x,
                EPSILON);
        assertEquals(1.0D,
                MudOrientation.orientPoint(support, Direction.WEST).x,
                EPSILON);
        assertEquals(0.875D,
                MudOrientation.orientPoint(exposed, Direction.WEST).x,
                EPSILON);
        assertEquals(1.0D,
                MudOrientation.orientPoint(support, Direction.DOWN).y,
                EPSILON);
        assertEquals(0.875D,
                MudOrientation.orientPoint(exposed, Direction.DOWN).y,
                EPSILON);
    }

    @Test
    void aTwoPixelWallLayerKeepsItsRealThickness() {
        AABB bounds = MudOrientation.layerBounds(
                Direction.EAST, 2.0D / 16.0D);

        assertEquals(0.0D, bounds.minX, EPSILON);
        assertEquals(0.125D, bounds.maxX, EPSILON);
        assertTrue(bounds.contains(new Vec3(0.08D, 0.5D, 0.5D)));
        assertFalse(bounds.contains(new Vec3(0.30D, 0.5D, 0.5D)));
    }

    @Test
    void stackFillDoesNotChangeTheConfiguredAttachmentDirection() {
        assertEquals(Direction.EAST, MudOrientation.surfaceDirection(2, Direction.EAST));
        assertEquals(Direction.UP, MudOrientation.surfaceDirection(16, Direction.EAST));
    }
}
