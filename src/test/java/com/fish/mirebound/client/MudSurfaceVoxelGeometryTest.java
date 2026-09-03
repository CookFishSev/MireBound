package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudSurfaceVoxelGeometryTest {
    @Test
    void missingNeighborLeavesTheWholeWallVisible() {
        assertEquals(0.0D, MudSurfaceVoxelGeometry.visibleWallStart(
                12.0D, 0.08D, MudSurfaceVoxelGeometry.NO_NEIGHBOR), 1.0E-9D);
    }

    @Test
    void equallyTallNeighborHidesTheSharedWall() {
        assertEquals(0.08D, MudSurfaceVoxelGeometry.visibleWallStart(
                12.0D, 0.08D, 12.08D), 1.0E-9D);
    }

    @Test
    void shorterNeighborLeavesOnlyTheHeightDifferenceVisible() {
        assertEquals(0.03D, MudSurfaceVoxelGeometry.visibleWallStart(
                12.0D, 0.08D, 12.03D), 1.0E-9D);
    }

    @Test
    void raisedNeighborBaseCanHideTheWholeWall() {
        assertEquals(0.08D, MudSurfaceVoxelGeometry.visibleWallStart(
                12.0D, 0.08D, 12.20D), 1.0E-9D);
    }

    @Test
    void neighborBelowTheBaseDoesNotHideTheWall() {
        assertEquals(0.0D, MudSurfaceVoxelGeometry.visibleWallStart(
                12.0D, 0.08D, 11.95D), 1.0E-9D);
    }

    @Test
    void residualPileDoesNotOccludeWhileTheCellRendersAsADepression() {
        assertEquals(false, MudSurfaceVoxelGeometry.rendersAsPile(
                0.004D, 0.08D, 0.001D));
        assertEquals(true, MudSurfaceVoxelGeometry.rendersAsPile(
                0.003D, 0.08D, 0.001D));
    }

    @Test
    void detectsLeftHandedTopSurfaceBasisUsedByWorldMud() {
        assertTrue(MudSurfaceVoxelGeometry.reverseWinding(
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(0.0D, 1.0D, 0.0D)));
        assertFalse(MudSurfaceVoxelGeometry.reverseWinding(
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D)));
    }

    @Test
    void omitsSubPixelHeightDifferencesButKeepsVisibleWalls() {
        assertFalse(MudSurfaceVoxelGeometry.wallVisible(
                0.0400D, 0.0408D, 0.0010D));
        assertTrue(MudSurfaceVoxelGeometry.wallVisible(
                0.0380D, 0.0400D, 0.0010D));
    }
}
