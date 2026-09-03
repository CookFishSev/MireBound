package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudEntityGeometryPoseTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    void headFacesStayUprightAtZeroPitch() {
        MudEntityGeometry.SamplingBasis basis =
                MudEntityGeometry.orientedBasis(Vec3.ZERO, 1.0D / 16.0D, 0.0F, 0.0F, 24.0D);

        assertVec(new Vec3(0.0D, 1.0D, 0.0D),
                MudEntityGeometry.surfacePixelOutwardNormal(basis, MudSurface.TOP));
        assertVec(new Vec3(0.0D, 0.0D, 1.0D),
                MudEntityGeometry.surfacePixelOutwardNormal(basis, MudSurface.FRONT));
    }

    @Test
    void lookingDownRotatesTopForwardAndFaceDown() {
        MudEntityGeometry.SamplingBasis basis =
                MudEntityGeometry.orientedBasis(Vec3.ZERO, 1.0D / 16.0D, 0.0F, 90.0F, 24.0D);

        assertVec(new Vec3(0.0D, 0.0D, 1.0D),
                MudEntityGeometry.surfacePixelOutwardNormal(basis, MudSurface.TOP));
        assertVec(new Vec3(0.0D, -1.0D, 0.0D),
                MudEntityGeometry.surfacePixelOutwardNormal(basis, MudSurface.FRONT));

        Vec3 topPixel = MudEntityGeometry.surfacePixelPoint(
                basis, MudBodyPart.HEAD, MudSurface.TOP, 3, 3);
        assertTrue(topPixel.z > 0.49D, "The top of a downward-looking head should move in front of the neck");
    }

    @Test
    void headRotationKeepsTheNeckPivotFixed() {
        MudEntityGeometry.SamplingBasis level =
                MudEntityGeometry.orientedBasis(Vec3.ZERO, 1.0D / 16.0D, 35.0F, 0.0F, 24.0D);
        MudEntityGeometry.SamplingBasis pitched =
                MudEntityGeometry.orientedBasis(Vec3.ZERO, 1.0D / 16.0D, 35.0F, -55.0F, 24.0D);

        assertVec(level.pivot(), pitched.pivot());
        assertEquals(1.5D, pitched.pivot().y, EPSILON);
    }

    @Test
    void topDownSplashMapsOntoTheFaceWhenLookingUp() {
        MudEntityGeometry.SamplingBasis basis =
                MudEntityGeometry.orientedBasis(
                        Vec3.ZERO, 1.0D / 16.0D, 0.0F, -90.0F, 24.0D);

        Vec3 surface = MudEntityGeometry.nearestSurfacePoint(
                basis, MudBodyPart.HEAD, new Vec3(0.0D, 2.0D, 0.0D));

        assertEquals(1.75D, surface.y, EPSILON);
        assertEquals(0.0D, surface.z, EPSILON);
        assertVec(new Vec3(0.0D, 1.0D, 0.0D),
                MudEntityGeometry.surfacePixelOutwardNormal(
                        basis, MudSurface.FRONT));
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
