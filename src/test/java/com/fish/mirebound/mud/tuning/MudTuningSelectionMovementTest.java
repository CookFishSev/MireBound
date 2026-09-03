package com.fish.mirebound.mud.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MudTuningSelectionMovementTest {
    private static final BlockPos FIRST = new BlockPos(3, 7, 11);
    private static final BlockPos SECOND = new BlockPos(9, 13, 17);

    @Test
    void movesOnlyTheSelectedCorner() {
        MudTuningSelectionMovement.Result first = MudTuningSelectionMovement.move(
                FIRST, SECOND, MudTuningSelectionElement.FIRST, Direction.WEST);
        MudTuningSelectionMovement.Result second = MudTuningSelectionMovement.move(
                FIRST, SECOND, MudTuningSelectionElement.SECOND, Direction.UP);

        assertEquals(FIRST.west(), first.first());
        assertEquals(SECOND, first.second());
        assertEquals(FIRST, second.first());
        assertEquals(SECOND.above(), second.second());
    }

    @Test
    void movingTheBodyPreservesItsDimensions() {
        MudTuningSelectionMovement.Result moved = MudTuningSelectionMovement.move(
                FIRST, SECOND, MudTuningSelectionElement.BODY, Direction.SOUTH);

        assertEquals(FIRST.south(), moved.first());
        assertEquals(SECOND.south(), moved.second());
        assertEquals(SECOND.subtract(FIRST), moved.second().subtract(moved.first()));
    }

    @Test
    void rejectsElementsThatNeedAMissingPoint() {
        assertNull(MudTuningSelectionMovement.move(
                FIRST, null, MudTuningSelectionElement.SECOND, Direction.NORTH));
        assertNull(MudTuningSelectionMovement.move(
                FIRST, null, MudTuningSelectionElement.BODY, Direction.NORTH));
        assertNull(MudTuningSelectionMovement.move(
                FIRST, SECOND, MudTuningSelectionElement.NONE, Direction.NORTH));
    }
}
