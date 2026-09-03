package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudSideSurfaceEffectManagerTest {
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
