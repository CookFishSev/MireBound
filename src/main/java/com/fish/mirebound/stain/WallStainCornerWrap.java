package com.fish.mirebound.stain;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** Builds stable stain strips around exposed outer edges of one support cube. */
public final class WallStainCornerWrap {
    private static final int GRID_SIZE = 16;

    private WallStainCornerWrap() {
    }

    public static List<WrappedFace> build(Direction sourceFace, long[] sourcePixels,
            int maximumPixels, float minimumSourceCoverage, float retention, float roughness,
            long fallbackCreatedAt, long stableSeed) {
        int maximum = Mth.clamp(maximumPixels, 0, 4);
        if (maximum == 0 || sourcePixels.length == 0) {
            return List.of();
        }
        long[] sourceByCell = strongestByCell(sourcePixels);
        Map<Direction, long[]> wrapped = new EnumMap<>(Direction.class);
        for (int along = 0; along < GRID_SIZE; along++) {
            wrapEdgePixel(sourceFace, sourceByCell, wrapped, 0, along, true, false,
                    maximum, minimumSourceCoverage, retention, roughness,
                    fallbackCreatedAt, stableSeed);
            wrapEdgePixel(sourceFace, sourceByCell, wrapped, GRID_SIZE - 1, along, true, true,
                    maximum, minimumSourceCoverage, retention, roughness,
                    fallbackCreatedAt, stableSeed);
            wrapEdgePixel(sourceFace, sourceByCell, wrapped, along, 0, false, false,
                    maximum, minimumSourceCoverage, retention, roughness,
                    fallbackCreatedAt, stableSeed);
            wrapEdgePixel(sourceFace, sourceByCell, wrapped, along, GRID_SIZE - 1, false, true,
                    maximum, minimumSourceCoverage, retention, roughness,
                    fallbackCreatedAt, stableSeed);
        }

        List<WrappedFace> result = new ArrayList<>(wrapped.size());
        for (Map.Entry<Direction, long[]> entry : wrapped.entrySet()) {
            long[] pixels = compact(entry.getValue());
            if (pixels.length == 0) {
                continue;
            }
            long strongest = strongestPixel(pixels);
            result.add(new WrappedFace(
                    entry.getKey(),
                    pixels,
                    MudFootprintBlockEntity.wallPixelStrength(strongest),
                    MudFootprintBlockEntity.wallPixelMedium(strongest)));
        }
        return result;
    }

    private static void wrapEdgePixel(Direction sourceFace, long[] sourceByCell,
            Map<Direction, long[]> wrapped, int x, int y,
            boolean horizontalEdge, boolean maximumEdge,
            int maximumPixels, float minimumSourceCoverage, float retention,
            float roughness, long fallbackCreatedAt, long stableSeed) {
        long source = sourceByCell[x | y << 4];
        if (source == 0L) {
            return;
        }
        int along = horizontalEdge ? y : x;
        float centerStrength = MudFootprintBlockEntity.wallPixelStrength(source);
        float smoothedStrength = centerStrength * 0.60F
                + edgeStrength(sourceByCell, horizontalEdge, maximumEdge, along - 1) * 0.20F
                + edgeStrength(sourceByCell, horizontalEdge, maximumEdge, along + 1) * 0.20F;
        float normalized = Mth.clamp(
                (smoothedStrength - minimumSourceCoverage)
                        / Math.max(0.01F, 1.0F - minimumSourceCoverage),
                0.0F,
                1.0F);
        long contourSeed = stableSeed
                ^ (long) sourceFace.ordinal() * 0x9e3779b97f4a7c15L
                ^ (horizontalEdge ? 0x632be59bd9b4e019L : 0x94d049bb133111ebL)
                ^ (maximumEdge ? 0x369dea0f31a53f85L : 0x5deece66dL);
        float coarseNoise = unitNoise(mix(
                contourSeed ^ (long) (along / 3) * 0x27d4eb2f165667c5L));
        float fineNoise = unitNoise(mix(
                contourSeed ^ (long) along * 0x165667b19e3779f9L));
        float contourNoise = coarseNoise * 0.72F + fineNoise * 0.28F;
        normalized = Mth.clamp(
                normalized + (contourNoise - 0.5F)
                        * Mth.clamp(roughness, 0.0F, 1.0F) * 1.10F,
                0.0F,
                1.0F);
        int length = maximumPixels == 1
                ? 1
                : 1 + Math.min(maximumPixels - 1, Mth.floor(normalized * maximumPixels));

        Direction.Axis crossedAxis = horizontalEdge
                ? horizontalAxis(sourceFace)
                : verticalAxis(sourceFace);
        Direction targetFace = axisDirection(crossedAxis, maximumEdge);
        long[] target = wrapped.computeIfAbsent(
                targetFace, ignored -> new long[GRID_SIZE * GRID_SIZE]);
        SinkingMedium medium = MudFootprintBlockEntity.wallPixelMedium(source);
        long createdAt = MudFootprintBlockEntity.wallPixelHasCreationTime(source)
                ? MudFootprintBlockEntity.wallPixelCreatedAt(source)
                : fallbackCreatedAt;
        float edgeStrength = (centerStrength * 0.65F + smoothedStrength * 0.35F)
                * Mth.lerp(Mth.clamp(roughness, 0.0F, 1.0F),
                        1.0F, 0.82F + contourNoise * 0.24F);

        int[] coordinates = sourceCoordinates(sourceFace, x, y);
        Direction.Axis sourceAxis = sourceFace.getAxis();
        boolean sourcePointsPositive = sourceFace.getAxisDirection()
                == Direction.AxisDirection.POSITIVE;
        for (int depth = 0; depth < length; depth++) {
            coordinates[sourceAxis.ordinal()] = sourcePointsPositive
                    ? GRID_SIZE - 1 - depth
                    : depth;
            int targetX = coordinates[horizontalAxis(targetFace).ordinal()];
            int targetY = coordinates[verticalAxis(targetFace).ordinal()];
            float strength = edgeStrength * (float) Math.pow(retention, depth + 1);
            putStrongest(target, targetX, targetY, strength, medium, createdAt);
        }
    }

    private static long[] strongestByCell(long[] sourcePixels) {
        long[] result = new long[GRID_SIZE * GRID_SIZE];
        for (long pixel : sourcePixels) {
            int cell = MudFootprintBlockEntity.wallPixelHorizontal(pixel)
                    | MudFootprintBlockEntity.wallPixelVertical(pixel) << 4;
            if (result[cell] == 0L
                    || MudFootprintBlockEntity.wallPixelStrength(pixel)
                            > MudFootprintBlockEntity.wallPixelStrength(result[cell])) {
                result[cell] = pixel;
            }
        }
        return result;
    }

    private static float edgeStrength(long[] sourceByCell, boolean horizontalEdge,
            boolean maximumEdge, int along) {
        if (along < 0 || along >= GRID_SIZE) {
            return 0.0F;
        }
        int fixed = maximumEdge ? GRID_SIZE - 1 : 0;
        int x = horizontalEdge ? fixed : along;
        int y = horizontalEdge ? along : fixed;
        long pixel = sourceByCell[x | y << 4];
        return pixel == 0L ? 0.0F : MudFootprintBlockEntity.wallPixelStrength(pixel);
    }

    private static int[] sourceCoordinates(Direction face, int horizontal, int vertical) {
        int[] coordinates = {GRID_SIZE / 2, GRID_SIZE / 2, GRID_SIZE / 2};
        coordinates[horizontalAxis(face).ordinal()] = horizontal;
        coordinates[verticalAxis(face).ordinal()] = vertical;
        coordinates[face.getAxis().ordinal()] = face.getAxisDirection()
                == Direction.AxisDirection.NEGATIVE ? 0 : GRID_SIZE - 1;
        return coordinates;
    }

    private static Direction.Axis horizontalAxis(Direction face) {
        return face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    private static Direction.Axis verticalAxis(Direction face) {
        return face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    private static Direction axisDirection(Direction.Axis axis, boolean positive) {
        return switch (axis) {
            case X -> positive ? Direction.EAST : Direction.WEST;
            case Y -> positive ? Direction.UP : Direction.DOWN;
            case Z -> positive ? Direction.SOUTH : Direction.NORTH;
        };
    }

    private static void putStrongest(long[] target, int x, int y, float strength,
            SinkingMedium medium, long createdAt) {
        int cell = x | y << 4;
        long packed = MudFootprintBlockEntity.packWallPixel(
                x, y, strength, medium, createdAt);
        if (target[cell] == 0L
                || MudFootprintBlockEntity.wallPixelStrength(packed)
                        > MudFootprintBlockEntity.wallPixelStrength(target[cell])) {
            target[cell] = packed;
        }
    }

    private static long strongestPixel(long[] pixels) {
        long strongest = 0L;
        for (long pixel : pixels) {
            if (strongest == 0L
                    || MudFootprintBlockEntity.wallPixelStrength(pixel)
                            > MudFootprintBlockEntity.wallPixelStrength(strongest)) {
                strongest = pixel;
            }
        }
        return strongest;
    }

    private static long[] compact(long[] pixels) {
        int count = 0;
        for (long pixel : pixels) {
            if (pixel != 0L) {
                count++;
            }
        }
        long[] compact = new long[count];
        int index = 0;
        for (long pixel : pixels) {
            if (pixel != 0L) {
                compact[index++] = pixel;
            }
        }
        return compact;
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

    public record WrappedFace(
            Direction face, long[] pixels, float strength, SinkingMedium medium) {
    }
}
