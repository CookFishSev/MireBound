package com.fish.mirebound.client;

import com.fish.mirebound.stain.MudFootprintBlockEntity;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** Selects sparse, world-aligned flow channels from a precise wall-stain frontier. */
final class MudWallFlowLayout {
    static final int GRID_SIZE = 16;
    static final int MAX_FLOWS_PER_FACE = 4;

    private MudWallFlowLayout() {
    }

    static int select(long[] cells, BlockPos blockPos, Direction face,
            int downstreamX, int downstreamY, boolean allowOutside,
            float configuredChance, long[] selectedPixels, long[] selectedHashes,
            float[] selectedScores) {
        if (cells.length != GRID_SIZE * GRID_SIZE
                || selectedPixels.length != selectedHashes.length
                || selectedPixels.length != selectedScores.length) {
            throw new IllegalArgumentException("invalid wall-flow scratch layout");
        }
        Arrays.fill(selectedScores, Float.POSITIVE_INFINITY);
        int limit = Math.min(MAX_FLOWS_PER_FACE, selectedPixels.length);
        if (limit == 0 || (downstreamX == 0 && downstreamY == 0)) {
            return 0;
        }

        float chance = Mth.clamp(configuredChance * 0.92F, 0.0F, 0.42F);
        if (chance <= 0.0F) {
            return 0;
        }
        int count = 0;
        int sideX = -downstreamY;
        int sideY = downstreamX;
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                long pixel = cells[cell(x, y)];
                if (pixel == 0L || !supportedFrontier(
                        cells, x, y, downstreamX, downstreamY, sideX, sideY, allowOutside)) {
                    continue;
                }

                int worldU = worldHorizontalCell(blockPos, face, x);
                int worldV = worldVerticalCell(blockPos, face, y);
                long hash = stableCellHash(worldU, worldV, face);
                float score = unitNoise(hash);
                float strength = MudFootprintBlockEntity.wallPixelStrength(pixel);
                float threshold = chance * (0.70F + strength * 0.30F);
                if (score > threshold
                        || score > unitNoise(stableCellHash(worldU - sideX, worldV - sideY, face))
                        || score > unitNoise(stableCellHash(worldU + sideX, worldV + sideY, face))) {
                    continue;
                }
                count = insertCandidate(pixel, hash, score,
                        selectedPixels, selectedHashes, selectedScores, count, limit);
            }
        }
        return count;
    }

    private static boolean supportedFrontier(long[] cells, int x, int y,
            int downstreamX, int downstreamY, int sideX, int sideY,
            boolean allowOutside) {
        int downstreamCellX = x + downstreamX;
        int downstreamCellY = y + downstreamY;
        boolean downstreamInside = inside(downstreamCellX, downstreamCellY);
        if ((!downstreamInside && !allowOutside)
                || (downstreamInside && occupied(cells, downstreamCellX, downstreamCellY))) {
            return false;
        }

        int upstreamX = x - downstreamX;
        int upstreamY = y - downstreamY;
        if (!occupied(cells, upstreamX, upstreamY)) {
            return false;
        }
        return occupied(cells, x + sideX, y + sideY)
                || occupied(cells, x - sideX, y - sideY)
                || occupied(cells, upstreamX + sideX, upstreamY + sideY)
                || occupied(cells, upstreamX - sideX, upstreamY - sideY);
    }

    private static int insertCandidate(long pixel, long hash, float score,
            long[] pixels, long[] hashes, float[] scores, int count, int limit) {
        int insertion = Math.min(count, limit);
        while (insertion > 0 && score < scores[insertion - 1]) {
            insertion--;
        }
        if (insertion >= limit) {
            return count;
        }
        int last = Math.min(count, limit - 1);
        for (int index = last; index > insertion; index--) {
            pixels[index] = pixels[index - 1];
            hashes[index] = hashes[index - 1];
            scores[index] = scores[index - 1];
        }
        pixels[insertion] = pixel;
        hashes[insertion] = hash;
        scores[insertion] = score;
        return Math.min(limit, count + 1);
    }

    private static boolean occupied(long[] cells, int x, int y) {
        return inside(x, y) && cells[cell(x, y)] != 0L;
    }

    private static boolean inside(int x, int y) {
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    private static int cell(int x, int y) {
        return x | y << 4;
    }

    static int worldHorizontalCell(BlockPos blockPos, Direction face, int localCell) {
        int blockCoordinate = face.getAxis() == Direction.Axis.X
                ? blockPos.getZ()
                : blockPos.getX();
        return blockCoordinate * GRID_SIZE + localCell;
    }

    static int worldVerticalCell(BlockPos blockPos, Direction face, int localCell) {
        int blockCoordinate = face.getAxis() == Direction.Axis.Y
                ? blockPos.getZ()
                : blockPos.getY();
        return blockCoordinate * GRID_SIZE + localCell;
    }

    private static long stableCellHash(int worldU, int worldV, Direction face) {
        long value = (long) worldU * 0x9e3779b97f4a7c15L;
        value ^= (long) worldV * 0xc2b2ae3d27d4eb4fL;
        value ^= (long) face.get3DDataValue() * 0x165667b19e3779f9L;
        return mix(value);
    }

    private static float unitNoise(long value) {
        return (value & 0xFFFFL) / 65535.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }
}
