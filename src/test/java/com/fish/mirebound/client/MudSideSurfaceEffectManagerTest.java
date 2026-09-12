package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.coverage.MudFeetContact;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudEntityGeometry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudSideSurfaceEffectManagerTest {
    @Test
    void supportedFootSliceDoesNotOpenASableFloorImprint() {
        Vec3 origin = new Vec3(10, -58.002, -4);
        Vec3 axisU = new Vec3(0, 0, 1);
        Vec3 axisV = new Vec3(1, 0, 0);
        var polygon = new MudEntityGeometry.SlicePolygon(MudBodyPart.LEFT_LEG,
                List.of(origin, origin.add(axisU), origin.add(axisU).add(axisV), origin.add(axisV)), .04);
        var slice = new MudEntityGeometry.OrientedPlaneSlice(origin, new Vec3(0, 1, 0),
                axisU, axisV, List.of(polygon));
        assertTrue(MudSideSurfaceEffectManager.reachesEntryPlane(slice, MudFeetContact.entryHeight(-58.079)));
        double entryY = MudFeetContact.entryHeight(-58.001);
        assertFalse(MudSideSurfaceEffectManager.reachesEntryPlane(slice, entryY));
        assertFalse(MudSideSurfaceEffectManager.cellReachesEntryPlane(origin, axisU, axisV, .5, .5, entryY));
    }

    @Test
    void actualImmersionStillDeformsTheFloor() {
        assertTrue(MudSideSurfaceEffectManager.cellReachesEntryPlane(new Vec3(10, -58.002, -4),
                new Vec3(0, 0, 1), new Vec3(1, 0, 0), .5, .5, MudFeetContact.entryHeight(-58.08)));
    }

    @Test
    void slopedAndSideFacesOnlyStampTheirPortionAboveTheSupportedFeet() {
        Vec3 origin = new Vec3(0, 1, 0);
        Vec3 axisU = new Vec3(1, 0, 0);
        Vec3 slopeV = new Vec3(0, .6, .8);
        double entryY = MudFeetContact.entryHeight(1);
        assertFalse(MudSideSurfaceEffectManager.cellReachesEntryPlane(origin, axisU, slopeV, .5, 0, entryY));
        assertTrue(MudSideSurfaceEffectManager.cellReachesEntryPlane(origin, axisU, slopeV, .5, .5, entryY));
        assertTrue(MudSideSurfaceEffectManager.cellReachesEntryPlane(origin, axisU, new Vec3(0, 1, 0), .5, .5, entryY));
        assertTrue(MudSideSurfaceEffectManager.cellReachesEntryPlane(new Vec3(0, 2, 0), axisU,
                new Vec3(0, 0, -1), .5, .5, entryY));
    }

    @Test
    void eruptionNeighborOffsetsStayInTheSelectedFacePlane() {
        BlockPos origin = new BlockPos(10, 20, 30);

        assertEquals(new BlockPos(12, 20, 27),
                MudSideSurfaceEffectManager.eruptionNeighborPos(
                        origin, Direction.DOWN, 2, -3));
        assertEquals(new BlockPos(12, 17, 30),
                MudSideSurfaceEffectManager.eruptionNeighborPos(
                        origin, Direction.NORTH, 2, -3));
        assertEquals(new BlockPos(10, 22, 27),
                MudSideSurfaceEffectManager.eruptionNeighborPos(
                        origin, Direction.WEST, 2, -3));
    }

    @Test
    void eruptionSearchCoversTheConfiguredMaximumWithoutUnboundedScanning() {
        assertEquals(1,
                MudSideSurfaceEffectManager.eruptionBlockSearchRadius(7.0D));
        assertEquals(2,
                MudSideSurfaceEffectManager.eruptionBlockSearchRadius(18.0D));
        assertEquals(2,
                MudSideSurfaceEffectManager.eruptionBlockSearchRadius(200.0D));
    }

    @Test
    void surfaceLightSamplesTheExposedSideOfEveryFace() {
        Vec3 center = new Vec3(10.5D, 20.0D, 30.5D);

        assertEquals(new BlockPos(10, 19, 30),
                MudSurfaceEffectManager.exposedSurfaceLightPosition(
                        center, new Vec3(0.0D, -1.0D, 0.0D)));
        assertEquals(new BlockPos(10, 20, 30),
                MudSurfaceEffectManager.exposedSurfaceLightPosition(
                        center, new Vec3(0.0D, 1.0D, 0.0D)));
        assertEquals(new BlockPos(9, 20, 30),
                MudSurfaceEffectManager.exposedSurfaceLightPosition(
                        new Vec3(10.0D, 20.5D, 30.5D),
                        new Vec3(-1.0D, 0.0D, 0.0D)));
    }

    @Test
    void sideClosureUsesItsActualRadiusInsteadOfClosingOneLayerPerTick() {
        assertEquals(0.04D,
                MudSideSurfaceEffectManager.closureRate(4.0D, 100.0D),
                1.0E-8D);
        assertEquals(0.01D,
                MudSideSurfaceEffectManager.closureRate(1.0D, 100.0D),
                1.0E-8D);
        assertEquals(1.0D,
                MudSideSurfaceEffectManager.closureRate(8.0D, 2.0D),
                1.0E-8D);
    }
}
