package com.fish.mirebound.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MudTerrainRotationTest {
    @Test
    void quarterTurnTransformsOffsetsAndReturnsAfterFourTurns() {
        MudTerrainRotation rotated = MudTerrainRotation.IDENTITY
                .rotate(Direction.Axis.Y);

        assertEquals(new BlockPos(0, 0, -1),
                rotated.apply(new BlockPos(1, 0, 0)));
        assertEquals(new BlockPos(1, 0, 0),
                rotated.apply(new BlockPos(0, 0, 1)));
        assertEquals(MudTerrainRotation.IDENTITY,
                rotated.rotate(Direction.Axis.Y)
                        .rotate(Direction.Axis.Y)
                        .rotate(Direction.Axis.Y));
    }

    @Test
    void onlyRightHandedOrthogonalBasesAreAccepted() {
        assertTrue(MudTerrainRotation.IDENTITY.valid());
        assertFalse(new MudTerrainRotation(
                Direction.EAST, Direction.UP, Direction.NORTH).valid());
        assertFalse(new MudTerrainRotation(
                Direction.EAST, Direction.WEST, Direction.SOUTH).valid());
    }
}
