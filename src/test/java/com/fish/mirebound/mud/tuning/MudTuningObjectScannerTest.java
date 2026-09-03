package com.fish.mirebound.mud.tuning;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MudTuningObjectScannerTest {
    @Test
    void ignoresAirAndInvisibleStainContainers() {
        assertTrue(MudTuningObjectScanner.isIgnoredState(true, false));
        assertTrue(MudTuningObjectScanner.isIgnoredState(false, true));
        assertFalse(MudTuningObjectScanner.isIgnoredState(false, false));
    }

    @Test
    void incompatibleSamplerKeepsNearestPositionsRegardlessOfScanOrder() {
        MudTuningHighlightGeometry.NearestPositions positions =
                new MudTuningHighlightGeometry.NearestPositions(3, BlockPos.ZERO);
        positions.offer(new BlockPos(100, 0, 0));
        positions.offer(new BlockPos(3, 0, 0));
        positions.offer(new BlockPos(2, 0, 0));
        positions.offer(new BlockPos(1, 0, 0));
        positions.offer(new BlockPos(50, 0, 0));

        assertArrayEquals(new long[] {
                BlockPos.asLong(1, 0, 0),
                BlockPos.asLong(2, 0, 0),
                BlockPos.asLong(3, 0, 0)
        }, positions.finish());
    }
}
