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

    static int growthStep(float age, int fadeInTicks, int durationTicks) {
        if (age <= fadeInTicks) return 0;
        double elapsed = Math.max(0, age - fadeInTicks);
        // At six time constants the rounded 0..128 result is already fully grown.
        if (elapsed >= Math.max(1, durationTicks) * 6.0) return 128;
        return (int) Math.round(Math.pow(1.0 - Math.exp(-elapsed / Math.max(1, durationTicks)), 1.32) * 128);
    }

    /** Rasterizes into the owning face, never a second overlapping render surface. */
    static void rasterize(long pixel, long hash, int growthStep, float maximumCells,
            double downU, double downV, boolean[] support, float[] coverage) {
        Arrays.fill(coverage, 0);
        if (growthStep <= 0 || downU * downU + downV * downV < 0.01) return;
        double startU = MudFootprintBlockEntity.wallPixelHorizontal(pixel) + 0.5 + downU * 0.5;
        double startV = MudFootprintBlockEntity.wallPixelVertical(pixel) + 0.5 + downV * 0.5;
        double length = Math.min(16, Math.max(0, maximumCells)) * growthStep / 128.0
                * (0.45 + Math.pow(unitNoise(mix(hash)), 1.28) * 0.67);
        // Stop at a hole or edge; an adjacent face owns its own transferred pixels.
        for (double distance = 0; distance <= length; distance += 0.25) {
            int x = Mth.floor(startU + downU * distance);
            int y = Mth.floor(startV + downV * distance);
            if (!inside(x, y) || (support != null && !support[cell(x, y)])) {
                length = distance;
                break;
            }
        }
        if (length <= 0) return;
        double endU = startU + downU * length;
        double endV = startV + downV * length;
        double halfWidth = 0.36 + unitNoise(hash) * 0.33;
        int minX = Math.max(0, Mth.floor(Math.min(startU, endU) - halfWidth));
        int maxX = Math.min(15, Mth.floor(Math.max(startU, endU) + halfWidth));
        int minY = Math.max(0, Mth.floor(Math.min(startV, endV) - halfWidth));
        int maxY = Math.min(15, Mth.floor(Math.max(startV, endV) + halfWidth));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int cell = cell(x, y);
                if (support != null && !support[cell]) continue;
                double u = x + 0.5 - startU;
                double v = y + 0.5 - startV;
                double along = u * downU + v * downV;
                double across = Math.abs(u * downV - v * downU);
                double area = Mth.clamp(halfWidth + 0.5 - across, 0, 1)
                        * Mth.clamp(along + 0.5, 0, 1) * Mth.clamp(length - along + 0.5, 0, 1);
                coverage[cell] = (float) (area * (0.25 + 0.35 * Mth.clamp(along / length, 0, 1)));
            }
        }
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
