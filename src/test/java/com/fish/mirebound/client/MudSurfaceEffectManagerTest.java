package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudSurfaceEffectManagerTest {
    @Test
    void renderedSlopeUsesVisualFeetDepthInsteadOfSteppedCollisionDepth() {
        assertEquals(0.24D, MudSurfaceEffectManager.renderedSurfaceDepth(
                0.01D, 64.74D, 64.50D), 1.0E-9D);
        assertEquals(0.0D, MudSurfaceEffectManager.renderedSurfaceDepth(
                0.20D, 64.40D, 64.50D), 1.0E-9D);
    }

    @Test
    void adaptiveSlopeMaskStaysOnItsProxyLayerAtTheLowEdge() {
        BlockPos proxy = new BlockPos(4, -60, 12);

        assertEquals(-60, MudSurfaceEffectManager.surfaceMaskLayer(
                -60.0D, proxy, true));
        assertEquals(-61, MudSurfaceEffectManager.surfaceMaskLayer(
                -60.0D, proxy, false));
    }

    @Test
    void impactDiskMergesIntoPlayerTopologyWithoutAnInnerPile() {
        MudSurfaceEffectManager.Hole impact = new MudSurfaceEffectManager.Hole(-1, 11L);
        MudSurfaceEffectManager.Hole pressure = new MudSurfaceEffectManager.Hole(1, 17L);
        MudSurfaceEffectManager.SurfaceCell impactCell = cell(4, 7, 0.72D);
        MudSurfaceEffectManager.SurfaceCell pressureCell = cell(4, 7, 0.90D);
        pressureCell.previousPileHeight = 0.75D;
        pressureCell.pileHeight = 0.82D;
        pressureCell.targetPileHeight = 0.68D;
        pressureCell.pileWeight = 1.20D;
        pressureCell.closureProgress = 0.35D;
        impactCell.refreshed = true;
        impact.maximumRadiusPixels = 8;
        pressure.maximumRadiusPixels = 3;
        impact.cells.put(MudSurfaceEffectManager.cellKey(4, 7), impactCell);
        pressure.cells.put(MudSurfaceEffectManager.cellKey(4, 7), pressureCell);

        MudSurfaceEffectManager.mergeImpactSurfaceCells(impact, pressure);

        assertSame(pressureCell,
                pressure.cells.get(MudSurfaceEffectManager.cellKey(4, 7)));
        assertEquals(0.90D, pressureCell.depression, 1.0E-9D);
        assertEquals(0.0D, pressureCell.pileHeight, 1.0E-9D);
        assertEquals(0.0D, pressureCell.targetPileHeight, 1.0E-9D);
        assertEquals(0.0D, pressureCell.pileWeight, 1.0E-9D);
        assertEquals(0.0D, pressureCell.closureProgress, 1.0E-9D);
        assertTrue(pressureCell.refreshed);
        assertEquals(8, pressure.maximumRadiusPixels);
    }

    @Test
    void distantPixelsOnTheSameSlopeRemainConnected() {
        Vec3 normal = new Vec3(-0.5D, 1.0D, 0.0D).normalize();
        MudRenderedSurfaceGeometry.SurfaceHit hit =
                new MudRenderedSurfaceGeometry.SurfaceHit(
                        0.0D, normal,
                        new Vec3(1.0D, 0.5D, 0.0D),
                        new Vec3(0.0D, 0.0D, 1.0D));
        MudSurfaceEffectManager.SurfaceCell first =
                slopedCell(0, 64.0D, hit);
        MudSurfaceEffectManager.SurfaceCell samePlane =
                slopedCell(4, 64.125D, hit);
        MudSurfaceEffectManager.SurfaceCell displaced =
                slopedCell(4, 64.30D, hit);

        assertTrue(MudSurfaceEffectManager.sameContinuousSurface(
                first, samePlane));
        assertTrue(!MudSurfaceEffectManager.sameContinuousSurface(
                first, displaced));
    }

    @Test
    void slopedPressureFootprintIsOneContinuousPlayerSizedArea() {
        var slice = MudSurfaceEffectManager.continuousPressureSlice(
                0.0D, 64.0D, 0.0D, 0.6D, 1.1D);

        assertEquals(1, slice.polygons().size());
        assertEquals(12, slice.polygons().getFirst().vertices().size());
        int covered = 0;
        for (int pixelX = -8; pixelX <= 8; pixelX++) {
            for (int pixelZ = -8; pixelZ <= 8; pixelZ++) {
                if (com.fish.mirebound.mud.MudEntityGeometry.containsXZ(
                        slice.polygons().getFirst().vertices(),
                        (pixelX + 0.5D) / 16.0D,
                        (pixelZ + 0.5D) / 16.0D)) {
                    covered++;
                }
            }
        }

        assertTrue(covered >= 72, "continuous slope footprint covered only " + covered);
        assertTrue(covered <= 96, "continuous slope footprint became oversized: " + covered);
    }

    @Test
    void supportCacheKeyDoesNotSharePositionSpecificModelDataAcrossSlopePixels() {
        MudSurfaceEffectManager.SurfaceCell first = cell(4, 7, 0.0D);
        MudSurfaceEffectManager.SurfaceCell adjacent = cell(5, 7, 0.0D);

        assertNotEquals(
                MudSurfaceEffectManager.surfaceSupportKey(first),
                MudSurfaceEffectManager.surfaceSupportKey(adjacent));
        assertEquals(
                MudSurfaceEffectManager.surfaceSupportKey(first),
                MudSurfaceEffectManager.surfaceSupportKey(cell(4, 7, 0.0D)));
    }

    private static MudSurfaceEffectManager.SurfaceCell cell(
            int pixelX, int pixelZ, double depression) {
        MudSurfaceEffectManager.SurfaceCell cell =
                new MudSurfaceEffectManager.SurfaceCell(
                        pixelX, pixelZ, 64.0D,
                        SinkingMedium.MUD, 0L, SinkingMedium.MUD, 31L);
        cell.depression = depression;
        return cell;
    }

    private static MudSurfaceEffectManager.SurfaceCell slopedCell(
            int pixelX, double surfaceY,
            MudRenderedSurfaceGeometry.SurfaceHit hit) {
        return new MudSurfaceEffectManager.SurfaceCell(
                pixelX, 0, surfaceY,
                SinkingMedium.MUD, 0L, SinkingMedium.MUD, 31L,
                hit, null);
    }
}
