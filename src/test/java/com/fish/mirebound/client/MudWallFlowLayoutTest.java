package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MudWallFlowLayoutTest {
    @Test
    void fullStainProducesOnlySparseBottomEdgeChannels() {
        long[] cells = filledCells();
        long[] pixels = new long[MudWallFlowLayout.MAX_FLOWS_PER_FACE];
        long[] hashes = new long[pixels.length];
        float[] scores = new float[pixels.length];

        int count = MudWallFlowLayout.select(cells, BlockPos.ZERO, Direction.NORTH,
                0, -1, true, 1.0F, pixels, hashes, scores);

        assertTrue(count > 0);
        assertTrue(count <= MudWallFlowLayout.MAX_FLOWS_PER_FACE);
        for (int index = 0; index < count; index++) {
            assertEquals(0, MudFootprintBlockEntity.wallPixelVertical(pixels[index]));
        }
    }

    @Test
    void unsupportedSinglePixelColumnsDoNotBecomeRibbons() {
        long[] cells = new long[16 * 16];
        for (int y = 0; y < 16; y++) {
            cells[5 | y << 4] = pixel(5, y);
        }
        long[] pixels = new long[MudWallFlowLayout.MAX_FLOWS_PER_FACE];
        long[] hashes = new long[pixels.length];
        float[] scores = new float[pixels.length];

        int count = MudWallFlowLayout.select(cells, BlockPos.ZERO, Direction.NORTH,
                0, -1, true, 1.0F, pixels, hashes, scores);

        assertEquals(0, count);
    }

    @Test
    void channelCoordinatesContinueAcrossBlockBoundaries() {
        assertEquals(15, MudWallFlowLayout.worldHorizontalCell(
                BlockPos.ZERO, Direction.NORTH, 15));
        assertEquals(16, MudWallFlowLayout.worldHorizontalCell(
                new BlockPos(1, 0, 0), Direction.NORTH, 0));
        assertEquals(16, MudWallFlowLayout.worldVerticalCell(
                new BlockPos(0, 1, 0), Direction.NORTH, 0));
    }

    private static long[] filledCells() {
        long[] cells = new long[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                cells[x | y << 4] = pixel(x, y);
            }
        }
        return cells;
    }

    private static long pixel(int x, int y) {
        return MudFootprintBlockEntity.packWallPixel(x, y, 1.0F, SinkingMedium.MUD);
    }
}
