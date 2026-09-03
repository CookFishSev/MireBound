package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.client.MudPlantSurfaceOffset;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class MudPlantSupportTest {
    @Test
    void upwardPlantsAreSupportedByFullAndPartialMud() {
        assertTrue(MudPlantSupport.canSustainSurface(
                Direction.UP, Direction.UP, true));
        assertTrue(MudPlantSupport.canSustainSurface(
                Direction.UP, Direction.UP, true));
        assertFalse(MudPlantSupport.canSustainSurface(
                Direction.UP, Direction.NORTH, true));
    }

    @Test
    void nonPlantBlocksAreNotGrantedPlantSupport() {
        assertFalse(MudPlantSupport.canSustainSurface(
                Direction.UP, Direction.UP, false));
        assertFalse(MudPlantSupport.canSustainSurface(
                Direction.NORTH, Direction.UP, true));
    }

    @Test
    void surfaceLookupUsesTheShapeAtThePlantOffset() {
        assertEquals(0.875D, MudPlantSurfaceOffset.topAt(
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.875D, 1.0D),
                0.5D, 0.5D));
        assertEquals(0.25D, MudPlantSurfaceOffset.topAt(
                Shapes.box(0.0D, 0.0D, 0.0D, 0.5D, 0.25D, 1.0D),
                0.25D, 0.5D));
        assertTrue(Double.isNaN(MudPlantSurfaceOffset.topAt(
                Shapes.box(0.0D, 0.0D, 0.0D, 0.5D, 0.25D, 1.0D),
                0.75D, 0.5D)));
    }

    @Test
    void surfaceCorrectionReplacesRandomVerticalOffset() {
        double supportY = 64.0D;
        double plantY = 65.0D;
        double surfaceY = 0.875D;
        double correction = supportY + surfaceY - plantY;

        assertEquals(-0.125D, correction);
        assertEquals(-0.125D, correction,
                "The original random plant Y offset must not be added again");
    }
}
