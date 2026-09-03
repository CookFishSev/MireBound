package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudEntityGeometryTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void sharedPixelGeometryMatchesCanonicalPlayerScale() {
        MudEntityGeometry.SamplingBasis basis = MudEntityGeometry.orientedBasis(
                Vec3.ZERO, 1.0D / 16.0D, 0.0F, 0.0F, 0.0D);

        Vec3 leftSole = MudEntityGeometry.surfacePixelPoint(
                basis, MudBodyPart.LEFT_LEG, MudSurface.BOTTOM, 1, 1);
        Vec3 bodyFront = MudEntityGeometry.surfacePixelPoint(
                basis, MudBodyPart.BODY, MudSurface.FRONT, 5, 3);

        assertEquals(0.09375D, leftSole.x, EPSILON);
        assertTrue(leftSole.y < 0.0D);
        assertTrue(bodyFront.y > 0.75D);
        assertTrue(bodyFront.z > 0.125D);
    }

    @Test
    void sweptSliceHullKeepsOuterBodyExtentWithoutFillingOutside() {
        List<Vec3> hull = MudEntityGeometry.convexHull(List.of(
                new Vec3(-0.25D, 1.0D, -0.125D),
                new Vec3(0.25D, 1.0D, -0.125D),
                new Vec3(0.25D, 1.0D, 0.125D),
                new Vec3(-0.25D, 1.0D, 0.125D),
                new Vec3(0.0D, 1.0D, 0.0D)));

        assertEquals(4, hull.size());
        assertTrue(MudEntityGeometry.containsXZ(hull, 0.0D, 0.0D));
        assertTrue(MudEntityGeometry.containsXZ(hull, 0.24D, 0.10D));
        assertFalse(MudEntityGeometry.containsXZ(hull, 0.30D, 0.0D));
    }

    @Test
    void verticalPlaneContainmentUsesFaceLocalCoordinates() {
        Vec3 origin = new Vec3(4.0D, 8.0D, 12.0D);
        Vec3 axisU = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 axisV = new Vec3(0.0D, 1.0D, 0.0D);
        List<Vec3> polygon = List.of(
                origin,
                origin.add(axisU.scale(0.5D)),
                origin.add(axisU.scale(0.5D)).add(axisV),
                origin.add(axisV));

        assertTrue(MudEntityGeometry.containsPlane(
                polygon, origin, axisU, axisV, 0.25D, 0.5D));
        assertFalse(MudEntityGeometry.containsPlane(
                polygon, origin, axisU, axisV, 0.60D, 0.5D));
        assertFalse(MudEntityGeometry.containsPlane(
                polygon, origin, axisU, axisV, 0.25D, 1.1D));
    }
}
