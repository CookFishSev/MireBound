package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Stable world-space sampling that prevents wall stains from repeating once per block. */
final class MudWallTextureContinuity {
    private static final int GRID_SIZE = 16;
    private static final int SAMPLE_CELL_SIZE = 13;
    private static final int TEXTURE_PERIOD = 16;
    private static final float LOW_ALPHA_CARDINAL_MIX = 0.42F;
    private static final float HIGH_ALPHA_CARDINAL_MIX = 0.20F;
    private static final float LOW_ALPHA_DIAGONAL_MIX = 0.08F;
    private static final float HIGH_ALPHA_DIAGONAL_MIX = 0.03F;
    private static final int OPACITY_SMOOTHING_CEILING = 224;
    private static final int[][] CARDINAL_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] DIAGONAL_OFFSETS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    private MudWallTextureContinuity() {
    }

    static int sampleAbgr(SinkingMedium medium, long visualSource, Direction face,
            int worldX, int worldY, int alpha) {
        int salt = textureSalt(medium, visualSource, face);
        int gridX = Math.floorDiv(worldX, SAMPLE_CELL_SIZE);
        int gridY = Math.floorDiv(worldY, SAMPLE_CELL_SIZE);
        float blendX = smooth(Math.floorMod(worldX, SAMPLE_CELL_SIZE)
                / (float) SAMPLE_CELL_SIZE);
        float blendY = smooth(Math.floorMod(worldY, SAMPLE_CELL_SIZE)
                / (float) SAMPLE_CELL_SIZE);
        int top = blendRgbPreserveAlpha(
                sampleAtPhase(medium, visualSource, worldX, worldY, salt, alpha,
                        samplePhase(gridX, gridY, salt)),
                sampleAtPhase(medium, visualSource, worldX, worldY, salt, alpha,
                        samplePhase(gridX + 1, gridY, salt)),
                blendX);
        int bottom = blendRgbPreserveAlpha(
                sampleAtPhase(medium, visualSource, worldX, worldY, salt, alpha,
                        samplePhase(gridX, gridY + 1, salt)),
                sampleAtPhase(medium, visualSource, worldX, worldY, salt, alpha,
                        samplePhase(gridX + 1, gridY + 1, salt)),
                blendX);
        return blendRgbPreserveAlpha(top, bottom, blendY);
    }

    private static int sampleAtPhase(SinkingMedium medium, long visualSource,
            int worldX, int worldY, int salt, int alpha, int phase) {
        int offsetX = Math.floorMod(phase, TEXTURE_PERIOD);
        int offsetY = Math.floorMod(phase >>> 8, TEXTURE_PERIOD);
        return MudSkinTextureCache.skinCoverageTextureAbgr(
                medium, visualSource,
                worldX + offsetX, worldY + offsetY, salt, alpha);
    }

    static int textureSampleY(int blockPlaneOffset, int localY) {
        return GRID_SIZE - 1 - blockPlaneOffset - localY;
    }

    static int textureSalt(SinkingMedium medium, long visualSource, Direction face) {
        long value = visualSource;
        value ^= (long) medium.id() * 0x9e3779b97f4a7c15L;
        value ^= (long) face.get3DDataValue() * 0xc2b2ae3d27d4eb4fL;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return (int) (value ^ value >>> 33);
    }

    static int samplePhase(int gridX, int gridY, int seed) {
        int hash = seed;
        hash ^= gridX * 0x632BE5AB;
        hash = Integer.rotateLeft(hash, 13);
        hash ^= gridY * 0x85157AF5;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        hash *= 0x846CA68B;
        return hash ^ hash >>> 16;
    }

    static int blendRgbPreserveAlpha(int first, int second, float amount) {
        float blend = Mth.clamp(amount, 0.0F, 1.0F);
        return FastColor.ABGR32.color(
                FastColor.ABGR32.alpha(first),
                Mth.lerpInt(blend, FastColor.ABGR32.blue(first), FastColor.ABGR32.blue(second)),
                Mth.lerpInt(blend, FastColor.ABGR32.green(first), FastColor.ABGR32.green(second)),
                Mth.lerpInt(blend, FastColor.ABGR32.red(first), FastColor.ABGR32.red(second)));
    }

    static void stabilizeLowOpacity(int[] source, int[] target, boolean[] occupied,
            boolean[] stable, int gridSize) {
        if (source.length != target.length || source.length != occupied.length
                || source.length != stable.length || source.length != gridSize * gridSize) {
            throw new IllegalArgumentException("wall opacity grids must have matching square dimensions");
        }
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int cell = x + y * gridSize;
                int color = source[cell];
                if (!occupied[cell] || stable[cell]) {
                    target[cell] = color;
                    continue;
                }
                int centerAlpha = FastColor.ABGR32.alpha(color);
                int cardinalAverage = neighborAlphaAverage(
                        source, occupied, x, y, gridSize, CARDINAL_OFFSETS);
                int cardinalCount = neighborCount(occupied, x, y, gridSize, CARDINAL_OFFSETS);
                if (centerAlpha <= 0 || centerAlpha >= OPACITY_SMOOTHING_CEILING || cardinalCount < 2) {
                    target[cell] = color;
                    continue;
                }

                float opacity = centerAlpha / 255.0F;
                float cardinalMix = Mth.lerp(opacity,
                        LOW_ALPHA_CARDINAL_MIX, HIGH_ALPHA_CARDINAL_MIX);
                int alpha = cardinalAverage > centerAlpha
                        ? Mth.lerpInt(cardinalMix, centerAlpha, cardinalAverage)
                        : centerAlpha;
                int diagonalCount = neighborCount(occupied, x, y, gridSize, DIAGONAL_OFFSETS);
                if (diagonalCount >= 2) {
                    int diagonalAverage = neighborAlphaAverage(
                            source, occupied, x, y, gridSize, DIAGONAL_OFFSETS);
                    float diagonalMix = Mth.lerp(opacity,
                            LOW_ALPHA_DIAGONAL_MIX, HIGH_ALPHA_DIAGONAL_MIX);
                    if (diagonalAverage > alpha) {
                        alpha = Mth.lerpInt(diagonalMix, alpha, diagonalAverage);
                    }
                }
                target[cell] = FastColor.ABGR32.color(
                        Mth.clamp(alpha, 0, 255),
                        FastColor.ABGR32.blue(color),
                        FastColor.ABGR32.green(color),
                        FastColor.ABGR32.red(color));
            }
        }
    }

    private static int neighborAlphaAverage(int[] colors, boolean[] occupied,
            int x, int y, int gridSize, int[][] offsets) {
        int alpha = 0;
        int count = 0;
        for (int[] offset : offsets) {
            int neighborX = x + offset[0];
            int neighborY = y + offset[1];
            if (neighborX < 0 || neighborX >= gridSize || neighborY < 0 || neighborY >= gridSize) {
                continue;
            }
            int cell = neighborX + neighborY * gridSize;
            if (occupied[cell]) {
                alpha += FastColor.ABGR32.alpha(colors[cell]);
                count++;
            }
        }
        return count == 0 ? 0 : alpha / count;
    }

    private static int neighborCount(boolean[] occupied, int x, int y,
            int gridSize, int[][] offsets) {
        int count = 0;
        for (int[] offset : offsets) {
            int neighborX = x + offset[0];
            int neighborY = y + offset[1];
            if (neighborX >= 0 && neighborX < gridSize && neighborY >= 0 && neighborY < gridSize
                    && occupied[neighborX + neighborY * gridSize]) {
                count++;
            }
        }
        return count;
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }
}
