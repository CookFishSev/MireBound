package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MudWallFlowLayoutTest {
    @Test
    void growthFastPathsMatchTheCurveIncludingOldAndUntimedPixels() {
        for (int duration : new int[] {1, 48, 240}) {
            for (int age = 0; age < duration * 10; age++) {
                int expected = (int) Math.round(Math.pow(1.0 - Math.exp(-Math.max(0, age - 8)
                        / (double) duration), 1.32) * 128);
                assertEquals(expected, MudWallFlowLayout.growthStep(age, 8, duration));
            }
        }
        assertEquals(128, MudWallFlowLayout.growthStep(Integer.MAX_VALUE, 8, 48));
    }

    @Test
    void flowRasterStopsAtShapeHolesAndDoesNotJumpToTheOtherSide() {
        boolean[] support = new boolean[256];
        Arrays.fill(support, true);
        for (int x = 0; x < 16; x++) support[x | 8 << 4] = false;
        float[] coverage = new float[256];
        MudWallFlowLayout.rasterize(pixel(7, 12), 0, 128, 16, 0, -1, support, coverage);
        assertTrue(coverage[7 | 10 << 4] > 0);
        for (int y = 0; y <= 8; y++) {
            for (int x = 0; x < 16; x++) assertEquals(0, coverage[x | y << 4]);
        }
    }

    @Test
    void flowRasterFollowsSlopedLocalGravityWithoutWrappingAtFaceEdges() {
        float[] coverage = new float[256];
        double direction = Math.sqrt(0.5);
        MudWallFlowLayout.rasterize(pixel(10, 5), 0, 128, 16, direction, -direction, null, coverage);
        assertTrue(coverage[12 | 3 << 4] > 0);
        for (int y = 0; y < 16; y++) assertEquals(0, coverage[y << 4]);
        for (int x = 0; x < 16; x++) assertEquals(0, coverage[x | 15 << 4]);
    }

    @Test
    void growthIsBoundedAndMonotonicAndClearsThePreviousRaster() {
        int previous = 0;
        for (int age = 0; age < 1000; age++) {
            int step = MudWallFlowLayout.growthStep(age, 8, 48);
            assertTrue(step >= previous && step <= 128);
            previous = step;
        }
        float[] coverage = new float[256];
        Arrays.fill(coverage, 1);
        MudWallFlowLayout.rasterize(pixel(7, 12), 0, 0, 16, 0, -1, null, coverage);
        for (float value : coverage) assertEquals(0, value);
    }

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
