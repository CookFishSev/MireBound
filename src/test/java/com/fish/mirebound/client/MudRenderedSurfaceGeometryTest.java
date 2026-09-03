package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudRenderedSurfaceGeometryTest {
    @Test
    void slopedTriangleReturnsTheRenderedHeightInsteadOfAStepHeight() {
        MudRenderedSurfaceGeometry.SurfaceHit hit =
                MudRenderedSurfaceGeometry.intersectTriangle(
                        Direction.UP,
                        new Vec3(0.0D, 0.0D, 0.0D),
                        new Vec3(1.0D, 1.0D, 0.0D),
                        new Vec3(1.0D, 1.0D, 1.0D),
                        0.75D, 0.0D, 0.25D);

        assertEquals(0.75D, hit.coordinate(), 1.0E-6D);
        assertTrue(hit.normal().y > 0.0D);
        assertEquals(0.0D, hit.axisX().dot(hit.normal()), 1.0E-6D);
        assertEquals(0.0D, hit.axisZ().dot(hit.normal()), 1.0E-6D);
        assertEquals(1.0D, hit.axisX().y, 1.0E-6D);
    }

    @Test
    void queryOutsideTheRenderedTriangleDoesNotCreateSurfaceSupport() {
        MudRenderedSurfaceGeometry.SurfaceHit hit =
                MudRenderedSurfaceGeometry.intersectTriangle(
                        Direction.UP,
                        new Vec3(0.0D, 0.0D, 0.0D),
                        new Vec3(1.0D, 1.0D, 0.0D),
                        new Vec3(1.0D, 1.0D, 1.0D),
                        0.20D, 0.0D, 0.80D);

        assertNull(hit);
    }

    @Test
    void verticalRenderedFaceReturnsItsActualPlane() {
        MudRenderedSurfaceGeometry.SurfaceHit hit =
                MudRenderedSurfaceGeometry.intersectTriangle(
                        Direction.EAST,
                        new Vec3(0.65D, 0.0D, 0.0D),
                        new Vec3(0.65D, 1.0D, 0.0D),
                        new Vec3(0.65D, 1.0D, 1.0D),
                        0.0D, 0.75D, 0.25D);

        assertEquals(0.65D, hit.coordinate(), 1.0E-6D);
        assertTrue(hit.normal().x > 0.0D);
    }

    @Test
    void horizontalSurfaceKeepsTheExistingWorldGridBasis() {
        MudRenderedSurfaceGeometry.SurfaceHit hit =
                MudRenderedSurfaceGeometry.intersectTriangle(
                        Direction.UP,
                        new Vec3(0.0D, 0.75D, 0.0D),
                        new Vec3(1.0D, 0.75D, 1.0D),
                        new Vec3(1.0D, 0.75D, 0.0D),
                        0.5D, 0.0D, 0.5D);

        assertEquals(1.0D, hit.axisX().x, 1.0E-6D);
        assertEquals(0.0D, hit.axisX().y, 1.0E-6D);
        assertEquals(0.0D, hit.axisX().z, 1.0E-6D);
        assertEquals(0.0D, hit.axisZ().x, 1.0E-6D);
        assertEquals(0.0D, hit.axisZ().y, 1.0E-6D);
        assertEquals(1.0D, hit.axisZ().z, 1.0E-6D);
    }

    @Test
    void sourceTextureTransparencyRemovesInvisibleSurfacePixels() {
        MudRenderedSurfaceGeometry.TextureAlphaMask mask =
                MudRenderedSurfaceGeometry.testAlphaMask(
                        2, 1, new boolean[] {true, false});
        Vec3 first = new Vec3(0.0D, 0.5D, 0.0D);
        Vec3 second = new Vec3(1.0D, 0.5D, 0.0D);
        Vec3 third = new Vec3(1.0D, 0.5D, 1.0D);

        MudRenderedSurfaceGeometry.SurfaceHit visible =
                MudRenderedSurfaceGeometry.intersectTexturedTriangle(
                        Direction.UP,
                        first, 0.0F, 0.0F,
                        second, 1.0F, 0.0F,
                        third, 1.0F, 1.0F,
                        mask, 0.25D, 0.0D, 0.10D);
        MudRenderedSurfaceGeometry.SurfaceHit transparent =
                MudRenderedSurfaceGeometry.intersectTexturedTriangle(
                        Direction.UP,
                        first, 0.0F, 0.0F,
                        second, 1.0F, 0.0F,
                        third, 1.0F, 1.0F,
                        mask, 0.75D, 0.0D, 0.10D);

        assertNotNull(visible);
        assertNull(transparent);
    }

    @Test
    void diagonalBoundaryCellKeepsOnlyItsRenderedHalf() {
        double pixel = 1.0D / 16.0D;
        MudRenderedSurfaceGeometry.SurfacePatch patch =
                MudRenderedSurfaceGeometry.clipTriangleForTest(
                        Direction.UP,
                        new Vec3(0.0D, 0.5D, 0.0D),
                        new Vec3(pixel, 0.5D, 0.0D),
                        new Vec3(0.0D, 0.5D, pixel),
                        0, 0);

        assertNotNull(patch);
        assertFalse(patch.full());
        assertEquals(0.5D, patch.coverage(), 0.0001D);
        assertEquals(1, patch.polygons().size());
        assertEquals(3, patch.boundaryEdges().size());
        for (MudRenderedSurfaceGeometry.PatchVertex vertex
                : patch.polygons().getFirst().vertices()) {
            assertTrue(vertex.u() >= -0.5D - 1.0E-6D);
            assertTrue(vertex.u() <= 0.5D + 1.0E-6D);
            assertTrue(vertex.v() >= -0.5D - 1.0E-6D);
            assertTrue(vertex.v() <= 0.5D + 1.0E-6D);
        }
    }

    @Test
    void completelyCoveredCellKeepsTheFastFullVoxelPath() {
        MudRenderedSurfaceGeometry.SurfacePatch patch =
                MudRenderedSurfaceGeometry.clipTriangleForTest(
                        Direction.UP,
                        new Vec3(-1.0D, 0.5D, -1.0D),
                        new Vec3(-1.0D, 0.5D, 2.0D),
                        new Vec3(2.0D, 0.5D, -1.0D),
                        0, 0);

        assertNotNull(patch);
        assertTrue(patch.full());
        assertEquals(1.0D, patch.coverage(), 0.0001D);
        assertTrue(patch.polygons().isEmpty());
        assertTrue(patch.boundaryEdges().isEmpty());
    }

    @Test
    void partialPileHeightCompensatesLostAreaWithoutChangingFullCells() {
        double pixel = 1.0D / 16.0D;
        MudRenderedSurfaceGeometry.SurfacePatch partial =
                MudRenderedSurfaceGeometry.clipTriangleForTest(
                        Direction.UP,
                        new Vec3(0.0D, 0.5D, 0.0D),
                        new Vec3(pixel, 0.5D, 0.0D),
                        new Vec3(0.0D, 0.5D, pixel),
                        0, 0);
        MudRenderedSurfaceGeometry.SurfacePatch full =
                MudRenderedSurfaceGeometry.clipTriangleForTest(
                        Direction.UP,
                        new Vec3(-1.0D, 0.5D, -1.0D),
                        new Vec3(-1.0D, 0.5D, 2.0D),
                        new Vec3(2.0D, 0.5D, -1.0D),
                        0, 0);

        assertTrue(MudSurfaceVoxelRenderer.visiblePileHeight(0.04D, partial)
                > 0.04D);
        assertEquals(0.04D,
                MudSurfaceVoxelRenderer.visiblePileHeight(0.04D, full),
                0.0001D);
    }

    @Test
    void clippedTriangleUsesNonDegenerateQuadsWithTheSameArea() {
        MudRenderedSurfaceGeometry.PatchVertex first =
                new MudRenderedSurfaceGeometry.PatchVertex(0.0D, 0.0D);
        MudRenderedSurfaceGeometry.PatchVertex second =
                new MudRenderedSurfaceGeometry.PatchVertex(1.0D, 0.0D);
        MudRenderedSurfaceGeometry.PatchVertex third =
                new MudRenderedSurfaceGeometry.PatchVertex(0.0D, 1.0D);

        var quads = MudRenderedSurfaceGeometry.triangleQuads(
                first, second, third);

        assertEquals(3, quads.size());
        assertEquals(0.5D, quads.stream()
                .mapToDouble(MudRenderedSurfaceGeometryTest::polygonArea)
                .sum(), 0.0001D);
        for (MudRenderedSurfaceGeometry.PatchPolygon quad : quads) {
            assertEquals(4, quad.vertices().size());
            assertEquals(4, new java.util.HashSet<>(quad.vertices()).size());
            assertTrue(polygonArea(quad) > 0.0D);
        }
    }

    private static double polygonArea(
            MudRenderedSurfaceGeometry.PatchPolygon polygon) {
        double area = 0.0D;
        MudRenderedSurfaceGeometry.PatchVertex previous =
                polygon.vertices().getLast();
        for (MudRenderedSurfaceGeometry.PatchVertex current : polygon.vertices()) {
            area += previous.u() * current.v() - current.u() * previous.v();
            previous = current;
        }
        return area * 0.5D;
    }
}
