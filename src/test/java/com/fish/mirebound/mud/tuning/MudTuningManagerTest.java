package com.fish.mirebound.mud.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MudTuningManagerTest {
    @Test
    void refreshesOnlyAfterTheObservedWorldRevisionChanges() {
        assertFalse(MudTuningManager.refreshNeeded(17L, 17L));
        assertTrue(MudTuningManager.refreshNeeded(17L, 18L));
    }

    @Test
    void hugeSelectionKeepsABoundedHighlightWindowAroundThePlayer() {
        BlockPos[] bounds = MudTuningManager.boundedHighlightScanBounds(
                new BlockPos(-10_000, -64, -10_000),
                new BlockPos(10_000, 320, 10_000),
                new BlockPos(25, 80, -12), 48, 65_536);

        assertEquals(2, bounds.length);
        assertTrue(bounds[0].getX() <= 25 && bounds[1].getX() >= 25);
        assertTrue(bounds[0].getY() <= 80 && bounds[1].getY() >= 80);
        assertTrue(bounds[0].getZ() <= -12 && bounds[1].getZ() >= -12);
        long volume = (long) (bounds[1].getX() - bounds[0].getX() + 1)
                * (bounds[1].getY() - bounds[0].getY() + 1)
                * (bounds[1].getZ() - bounds[0].getZ() + 1);
        assertTrue(volume <= 65_536);
    }

    @Test
    void distantSelectionDoesNotTriggerAnUnrelatedHighlightScan() {
        BlockPos[] bounds = MudTuningManager.boundedHighlightScanBounds(
                new BlockPos(1_000, 0, 1_000), new BlockPos(2_000, 10, 2_000),
                BlockPos.ZERO, 48, 65_536);

        assertEquals(0, bounds.length);
    }
}
