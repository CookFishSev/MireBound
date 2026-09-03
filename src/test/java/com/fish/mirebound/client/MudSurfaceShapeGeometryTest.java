package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class MudSurfaceShapeGeometryTest {
    @Test
    void slabSideMaskRejectsTheEmptyUpperHalf() {
        AABB slab = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
        MudSurfaceShapeGeometry.FaceMask mask =
                MudSurfaceShapeGeometry.faceMask(
                        List.of(slab), slab, Direction.NORTH);

        assertTrue(mask.exposed(8, 3));
        assertFalse(mask.exposed(8, 12));
    }

    @Test
    void stairTopMaskLeavesTheCoveredHalfToTheUpperStep() {
        AABB lower = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
        AABB upper = new AABB(0.0D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D);
        MudSurfaceShapeGeometry.FaceMask lowerTop =
                MudSurfaceShapeGeometry.faceMask(
                        List.of(lower, upper), lower, Direction.UP);
        MudSurfaceShapeGeometry.FaceMask upperTop =
                MudSurfaceShapeGeometry.faceMask(
                        List.of(lower, upper), upper, Direction.UP);

        assertTrue(lowerTop.exposed(8, 3));
        assertFalse(lowerTop.exposed(8, 12));
        assertFalse(upperTop.exposed(8, 3));
        assertTrue(upperTop.exposed(8, 12));
    }

    @Test
    void stairRiserIsExposedButItsInternalLowerHalfIsNot() {
        AABB lower = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
        AABB upper = new AABB(0.0D, 0.5D, 0.5D, 1.0D, 1.0D, 1.0D);
        MudSurfaceShapeGeometry.FaceMask riser =
                MudSurfaceShapeGeometry.faceMask(
                        List.of(lower, upper), upper, Direction.NORTH);

        assertFalse(riser.exposed(8, 3));
        assertTrue(riser.exposed(8, 12));
    }

    @Test
    void supportCacheDistinguishesTwoHeightsInOneStairBlock() {
        long supportBlock = new BlockPos(0, 20, 0).asLong();
        MudSurfaceEffectManager.SurfaceCell lower =
                new MudSurfaceEffectManager.SurfaceCell(
                        0, 0, 20.5D, SinkingMedium.MUD,
                        supportBlock, SinkingMedium.MUD, 1L);
        MudSurfaceEffectManager.SurfaceCell upper =
                new MudSurfaceEffectManager.SurfaceCell(
                        1, 0, 21.0D, SinkingMedium.MUD,
                        supportBlock, SinkingMedium.MUD, 2L);

        assertNotEquals(
                MudSurfaceEffectManager.surfaceSupportKey(lower),
                MudSurfaceEffectManager.surfaceSupportKey(upper));
    }
}
